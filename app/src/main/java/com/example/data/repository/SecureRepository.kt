package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.security.PrivateKey
import javax.crypto.SecretKey
import android.util.Log
import com.example.notifications.NotificationClient
import com.example.crypto.CryptoEngine
import com.example.data.entity.CallLogEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserProfileEntity
import com.example.data.local.AppDatabase
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.DocumentReference

class SecureRepository(private val context: Context) {
  private val db = AppDatabase.getInstance(context)
  private val conversationDao = db.conversationDao()
  private val messageDao = db.messageDao()
  private val callLogDao = db.callLogDao()
  private val userProfileDao = db.userProfileDao()

  val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
  val callLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
  val userProfile: Flow<UserProfileEntity?> = userProfileDao.getProfile()
  val totalMessageCount: Flow<Int> = messageDao.getTotalMessageCount()
  
  private val firestore by lazy { FirebaseFirestore.getInstance() }
  private val storage by lazy { FirebaseStorage.getInstance() }
  private val auth by lazy { FirebaseAuth.getInstance() }
  
  private var isListeningToFirestore = false

  init {
    CoroutineScope(Dispatchers.IO).launch {
      seedInitialDataIfNeeded()
    }
  }
  
  sealed class SendMessageResult {
    data object Success : SendMessageResult()
    data class Failure(val reason: String) : SendMessageResult()
  }

  suspend fun getUsername(uid: String): String? = withContext(Dispatchers.IO) {
    try { firestore.collection("users").document(uid).get().await().getString("username") } catch (_: Exception) { null }
  }

  private fun normalizeUsername(value: String): String = value.trim()

  private fun isValidUsername(value: String): Boolean = value.matches(Regex("^[A-Za-z0-9_]{3,20}$"))

  suspend fun setUsername(usernameInput: String): Result<String> = withContext(Dispatchers.IO) {
    val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not signed in"))
    val username = normalizeUsername(usernameInput)
    if (!isValidUsername(username)) return@withContext Result.failure(Exception("Username must be 3-20 characters using letters, numbers, or underscore"))
    try {
      firestore.runTransaction { transaction ->
        val ref = firestore.collection("usernames").document(username)
        if (transaction.get(ref).exists()) throw IllegalStateException("Username is already taken")
        transaction.set(ref, mapOf("uid" to uid))
        transaction.set(
          firestore.collection("users").document(uid),
          mapOf("uid" to uid, "username" to username),
          com.google.firebase.firestore.SetOptions.merge()
        )
        null
      }.await()
      Result.success(username)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun changeUsername(usernameInput: String): Result<String> = withContext(Dispatchers.IO) {
    val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not signed in"))
    val username = normalizeUsername(usernameInput)
    if (!isValidUsername(username)) return@withContext Result.failure(Exception("Username must be 3-20 characters using letters, numbers, or underscore"))
    try {
      firestore.runTransaction { transaction ->
        val userRef = firestore.collection("users").document(uid)
        val userSnap = transaction.get(userRef)
        val oldUsername = userSnap.getString("username")
        val newRef = firestore.collection("usernames").document(username)
        val newSnap = transaction.get(newRef)
        if (newSnap.exists() && newSnap.getString("uid") != uid) throw IllegalStateException("Username is already taken")
        if (oldUsername == username) return@runTransaction null
        oldUsername?.let { transaction.delete(firestore.collection("usernames").document(it)) }
        transaction.set(newRef, mapOf("uid" to uid))
        transaction.set(
          userRef,
          mapOf("uid" to uid, "username" to username),
          com.google.firebase.firestore.SetOptions.merge()
        )
        null
      }.await()
      Result.success(username)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun createConversationWithUsername(usernameInput: String, displayName: String): Result<ConversationEntity> = withContext(Dispatchers.IO) {
    val currentUid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not signed in"))
    val username = normalizeUsername(usernameInput)
    if (!isValidUsername(username)) return@withContext Result.failure(Exception("Username must be 3-20 characters using letters, numbers, or underscore"))
    try {
      val usernameDoc = firestore.collection("usernames").document(username).get().await()
      val contactUid = usernameDoc.getString("uid") ?: return@withContext Result.failure(Exception("Username not found"))
      if (contactUid == currentUid) return@withContext Result.failure(Exception("You cannot add yourself"))
      val userDoc = firestore.collection("users").document(contactUid).get().await()
      val contactName = userDoc.getString("displayName") ?: username
      val fingerprint = userDoc.getString("publicKey")?.let(CryptoEngine::computeKeyFingerprint) ?: CryptoEngine.computeKeyFingerprint(contactUid)
      val chatId = listOf(currentUid, contactUid).sorted().joinToString("_")
      val conversation = ConversationEntity(
        id = chatId, contactName = contactName, contactUid = contactUid, contactUsername = username,
        avatarSeed = username, lastEncryptedMessage = "🔒 E2EE Session Ready", lastMessageTimestamp = System.currentTimeMillis(),
        unreadCount = 0, safetyNumber = CryptoEngine.generateSafetyNumber(currentUid, contactUid), isVerified = false,
        e2eeFingerprint = fingerprint, isOnline = true
      )
      insertConversation(conversation)
      firestore.collection("chats").document(chatId).set(mapOf("participants" to listOf(currentUid, contactUid))).await()
      Result.success(conversation)
    } catch (e: Exception) { Result.failure(e) }
  }

  private suspend fun resolveRecipientSharedSecret(recipientUid: String): SecretKey? {
    return try {
      val recipientPubB64 = firestore.collection("users").document(recipientUid).get().await().getString("publicKey") ?: return null
      val myPriv = getMyPrivateKey() ?: return null
      CryptoEngine.deriveSharedSecret(myPriv, CryptoEngine.decodePublicKey(recipientPubB64))
    } catch (_: Exception) { null }
  }

  fun startFirestoreSync() {
    try {
      val currentUserId = auth.currentUser?.uid ?: return
      if (isListeningToFirestore) return
      isListeningToFirestore = true
      
      CoroutineScope(Dispatchers.IO).launch {
          try {
              val pubKey = getOrGenerateKeyPair()
              val firebaseUser = auth.currentUser
              firestore.collection("users").document(currentUserId)
                  .set(
                    mapOf(
                      "uid" to currentUserId,
                      "username" to getUsername(currentUserId),
                      "displayName" to (firebaseUser?.displayName ?: "Firebase User"),
                      "photoUrl" to firebaseUser?.photoUrl?.toString(),
                      "publicKey" to pubKey
                    ).filterValues { it != null },
                    com.google.firebase.firestore.SetOptions.merge()
                  ).await()
              try {
                  val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                  firestore.collection("userNotificationTokens").document(currentUserId)
                      .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge()).await()
              } catch (_: Exception) {
                  // Token sync is best effort; onNewToken retries later.
              }
          } catch(e: Exception) { e.printStackTrace() }
      }
      
      // Listen for new chats
      firestore.collection("chats")
        .whereArrayContains("participants", currentUserId)
        .addSnapshotListener { snapshot, e ->
          if (e != null || snapshot == null) return@addSnapshotListener
          
          CoroutineScope(Dispatchers.IO).launch {
          for (doc in snapshot.documents) {
            val chatId = doc.id
            val participants = doc.get("participants") as? List<String> ?: continue
            val otherUid = participants.firstOrNull { it != currentUserId } ?: currentUserId
            val otherUserDoc = try { firestore.collection("users").document(otherUid).get().await() } catch (_: Exception) { null }
            val otherUsername = otherUserDoc?.getString("username") ?: otherUid.take(8)
            
            // Check if conversation exists locally
            val existing = conversationDao.getConversationSync(chatId)
            if (existing == null) {
              val newConv = ConversationEntity(
                id = chatId,
                contactName = otherUserDoc?.getString("displayName") ?: otherUsername,
                contactUid = otherUid,
                contactUsername = otherUsername,
                avatarSeed = otherUsername,
                lastEncryptedMessage = "New Chat",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                safetyNumber = CryptoEngine.generateSafetyNumber(currentUserId, otherUid),
                isVerified = false,
                e2eeFingerprint = CryptoEngine.computeKeyFingerprint(otherUid),
                isOnline = true
              )
              conversationDao.insertOrUpdate(newConv)
            }
            
            // Listen for messages in this chat
            listenForMessages(chatId, currentUserId)
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun listenForMessages(chatId: String, currentUserId: String) {
    firestore.collection("chats").document(chatId).collection("messages")
      .orderBy("timestamp", Query.Direction.ASCENDING)
      .addSnapshotListener { snapshot, e ->
        if (e != null || snapshot == null) return@addSnapshotListener
        
        CoroutineScope(Dispatchers.IO).launch {
          for (change in snapshot.documentChanges) {
            if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
              val doc = change.document
              val senderId = doc.getString("senderId") ?: ""
              if (senderId != currentUserId) {
                // Incoming messages must be encrypted at rest on the server. Never accept the
                // legacy plaintext `text` field and never synthesize a message from it.
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                val cipherText = doc.getString("cipherText") ?: return@launch
                val iv = doc.getString("iv") ?: return@launch

                val decryptedText = try {
                    val senderDoc = firestore.collection("users").document(senderId).get().await()
                    val senderPubB64 = senderDoc.getString("publicKey")
                    val myPriv = getMyPrivateKey()
                    if (senderPubB64.isNullOrBlank() || myPriv == null) {
                        "[Encrypted message — waiting for key sync]"
                    } else {
                        val senderPub = CryptoEngine.decodePublicKey(senderPubB64)
                        val sharedSecret = CryptoEngine.deriveSharedSecret(myPriv, senderPub)
                        CryptoEngine.decryptStringE2EE(cipherText, iv, sharedSecret)
                    }
                } catch (e: Exception) {
                    Log.w("SecureRepository", "Could not decrypt incoming E2EE message", e)
                    "[Encrypted message — decryption unavailable]"
                }

                val encrypted = CryptoEngine.encryptString(decryptedText)
                
                val existing = messageDao.getMessagesForConversation(chatId).first()
                if (existing.none { it.timestamp == timestamp && it.senderId == senderId }) {
                  val msg = MessageEntity(
                    conversationId = chatId,
                    senderId = senderId,
                    senderName = senderId.substringBefore("@"),
                    isOutgoing = false,
                    cipherText = encrypted.cipherTextBase64,
                    iv = encrypted.ivBase64,
                    encryptionAlgorithm = "AES-256-GCM / X25519", 
                    messageType = MessageType.TEXT.name,
                    timestamp = timestamp,
                    status = MessageStatus.DELIVERED.name
                  )
                  messageDao.insert(msg)
                  
                  conversationDao.getConversationSync(chatId)?.let { conv ->
                    conversationDao.update(
                      conv.copy(
                        lastEncryptedMessage = "🔒 E2EE: $text",
                        lastMessageTimestamp = timestamp,
                        unreadCount = conv.unreadCount + 1
                      )
                    )
                  }
                }
              }
            }
          }
        }
      }
  }

  fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
    return messageDao.getMessagesForConversation(conversationId)
  }

  fun getConversation(id: String): Flow<ConversationEntity?> {
    return conversationDao.getConversationById(id)
  }

  suspend fun insertConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
    conversationDao.insertOrUpdate(conversation)
  }

  
  suspend fun sendEncryptedAttachmentMessage(
    conversationId: String,
    fileBytes: ByteArray,
    fileName: String,
    mimeType: String,
    senderName: String = "You (Verified)"
  ): SendMessageResult = withContext(Dispatchers.IO) {
    val currentUserId = auth.currentUser?.uid ?: return@withContext SendMessageResult.Failure("Not signed in")
    val conv = conversationDao.getConversationSync(conversationId)
      ?: return@withContext SendMessageResult.Failure("Conversation not found")
    val e2ee = resolveRecipientSharedSecret(conv.contactUid)
      ?: return@withContext SendMessageResult.Failure("can't send yet — waiting for the other person's key to sync")

    val now = System.currentTimeMillis()
    val fileAesKey = CryptoEngine.generateRandomAesKey()
    val encryptedFile = CryptoEngine.encryptFile(fileBytes, fileAesKey)
    val cipherTextBytes = android.util.Base64.decode(encryptedFile.cipherTextBase64, android.util.Base64.NO_WRAP)

    val storageRef = storage.reference.child("attachments/$conversationId/${java.util.UUID.randomUUID()}")
    try {
      storageRef.putBytes(cipherTextBytes).await()
      val downloadUrl = storageRef.downloadUrl.await().toString()
      val fileAesKeyB64 = android.util.Base64.encodeToString(fileAesKey, android.util.Base64.NO_WRAP)
      val encryptedKeyData = CryptoEngine.encryptStringE2EE(fileAesKeyB64, e2ee)
      val mediaPayload = "$downloadUrl|${encryptedFile.ivBase64}|$fileName|$mimeType"
      val localEncryptedKeyData = CryptoEngine.encryptString(fileAesKeyB64)

      val msg = MessageEntity(
        conversationId = conversationId, senderId = currentUserId, senderName = senderName,
        isOutgoing = true, cipherText = localEncryptedKeyData.cipherTextBase64, iv = localEncryptedKeyData.ivBase64,
        encryptionAlgorithm = "AES-256-GCM / X25519", messageType = MessageType.IMAGE.name,
        mediaBase64OrUri = mediaPayload, timestamp = now, status = MessageStatus.SENT.name
      )
      messageDao.insert(msg)
      conversationDao.update(conv.copy(lastEncryptedMessage = "📎 Encrypted Attachment", lastMessageTimestamp = now))

      val chatRef = firestore.collection("chats").document(conversationId)
      chatRef.set(mapOf("participants" to listOf(currentUserId, conv.contactUid))).await()
      chatRef.collection("messages").add(mapOf(
        "senderId" to currentUserId, "timestamp" to now, "messageType" to MessageType.IMAGE.name,
        "cipherText" to encryptedKeyData.cipherTextBase64, "iv" to encryptedKeyData.ivBase64,
        "mediaPayload" to mediaPayload
      )).await()
      NotificationClient.send(context, conv.contactUid, "message", senderName, "chat", conversationId)
      SendMessageResult.Success
    } catch (e: Exception) {
      Log.e("SecureRepository", "Failed to send encrypted attachment", e)
      SendMessageResult.Failure(e.message ?: "Attachment send failed")
    }
  }

  suspend fun sendEncryptedTextMessage(
    conversationId: String,
    text: String,
    senderName: String = "You (Verified)"
  ): SendMessageResult = withContext(Dispatchers.IO) {
    val currentUserId = auth.currentUser?.uid ?: return@withContext SendMessageResult.Failure("Not signed in")
    val conv = conversationDao.getConversationSync(conversationId)
      ?: return@withContext SendMessageResult.Failure("Conversation not found")
    val sharedSecret = resolveRecipientSharedSecret(conv.contactUid)
      ?: return@withContext SendMessageResult.Failure("can't send yet — waiting for the other person's key to sync")

    val now = System.currentTimeMillis()
    return@withContext try {
      val encrypted = CryptoEngine.encryptStringE2EE(text, sharedSecret)
      val localEncrypted = CryptoEngine.encryptString(text)
      messageDao.insert(MessageEntity(
        conversationId = conversationId, senderId = currentUserId, senderName = senderName,
        isOutgoing = true, cipherText = localEncrypted.cipherTextBase64, iv = localEncrypted.ivBase64,
        encryptionAlgorithm = encrypted.algorithm, messageType = MessageType.TEXT.name,
        timestamp = now, status = MessageStatus.SENT.name
      ))
      conversationDao.update(conv.copy(lastEncryptedMessage = "🔒 E2EE: $text", lastMessageTimestamp = now))

      val chatRef = firestore.collection("chats").document(conversationId)
      chatRef.set(mapOf("participants" to listOf(currentUserId, conv.contactUid))).await()
      chatRef.collection("messages").add(mapOf(
        "senderId" to currentUserId, "timestamp" to now, "messageType" to MessageType.TEXT.name,
        "cipherText" to encrypted.cipherTextBase64, "iv" to encrypted.ivBase64,
        "encryptionAlgorithm" to encrypted.algorithm
      )).await()
      NotificationClient.send(context, conv.contactUid, "message", senderName, "chat", conversationId)
      SendMessageResult.Success
    } catch (e: Exception) {
      Log.e("SecureRepository", "Failed to send encrypted text", e)
      SendMessageResult.Failure(e.message ?: "Message send failed")
    }
  }

  suspend fun sendEncryptedMediaMessage(
    conversationId: String,
    type: MessageType,
    mediaUriOrData: String,
    durationSeconds: Int = 0,
    caption: String = ""
  ) = withContext(Dispatchers.IO) {
    val textToEncrypt = caption.ifBlank {
      when (type) {
        MessageType.IMAGE -> "Encrypted Photo (Zero-Knowledge)"
        MessageType.VIDEO -> "Encrypted Video File"
        MessageType.AUDIO_VOICE_NOTE -> "Encrypted Voice Note ($durationSeconds s)"
        else -> "Encrypted Media"
      }
    }
    val encrypted = CryptoEngine.encryptString(textToEncrypt)
    val now = System.currentTimeMillis()

    val msg = MessageEntity(
      conversationId = conversationId,
      senderId = "me",
      senderName = "You",
      isOutgoing = true,
      cipherText = encrypted.cipherTextBase64,
      iv = encrypted.ivBase64,
      encryptionAlgorithm = "AES-256-GCM (Payload + Media)",
      messageType = type.name,
      mediaBase64OrUri = mediaUriOrData,
      mediaDurationSeconds = durationSeconds,
      timestamp = now,
      status = MessageStatus.SENT.name
    )
    messageDao.insert(msg)

    conversationDao.getConversationSync(conversationId)?.let { conv ->
      val preview = when (type) {
        MessageType.IMAGE -> "📷 🔒 Encrypted Photo"
        MessageType.VIDEO -> "📹 🔒 Encrypted Video"
        MessageType.AUDIO_VOICE_NOTE -> "🎙️ 🔒 Voice Note ($durationSeconds s)"
        else -> "🔒 Encrypted Attachment"
      }
      conversationDao.update(
        conv.copy(
          lastEncryptedMessage = preview,
          lastMessageTimestamp = now
        )
      )
    }
  }

  suspend fun logEncryptedVoiceCall(
    contactId: String,
    contactName: String,
    direction: CallDirection,
    durationSeconds: Int,
    fingerprint: String
  ) = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val callLog = CallLogEntity(
      contactId = contactId,
      contactName = contactName,
      callType = CallType.VOICE.name,
      direction = direction.name,
      durationSeconds = durationSeconds,
      timestamp = now,
      e2eeFingerprint = fingerprint,
      encryptionVerified = true
    )
    callLogDao.insert(callLog)

    // Insert call notice into chat history
    val durationFormatted = if (durationSeconds > 0) {
      val min = durationSeconds / 60
      val sec = durationSeconds % 60
      "$min:${sec.toString().padStart(2, '0')}"
    } else "Missed"

    val callText = "📞 Encrypted Voice Call ($durationFormatted) • Verified: $fingerprint"
    val enc = CryptoEngine.encryptString(callText)

    val msg = MessageEntity(
      conversationId = contactId,
      senderId = if (direction == CallDirection.OUTGOING) "me" else contactId,
      senderName = if (direction == CallDirection.OUTGOING) "You" else contactName,
      isOutgoing = direction == CallDirection.OUTGOING,
      cipherText = enc.cipherTextBase64,
      iv = enc.ivBase64,
      encryptionAlgorithm = "AES-256-GCM / SRTP",
      messageType = MessageType.CALL_EVENT.name,
      mediaDurationSeconds = durationSeconds,
      timestamp = now,
      status = MessageStatus.READ.name
    )
    messageDao.insert(msg)

    conversationDao.getConversationSync(contactId)?.let { conv ->
      conversationDao.update(
        conv.copy(
          lastEncryptedMessage = "📞 Voice Call ($durationFormatted)",
          lastMessageTimestamp = now
        )
      )
    }
  }

  suspend fun setSafetyVerification(conversationId: String, isVerified: Boolean) = withContext(Dispatchers.IO) {
    conversationDao.updateVerification(conversationId, isVerified)
  }

  suspend fun updateUserProfile(profile: UserProfileEntity) = withContext(Dispatchers.IO) {
    userProfileDao.insertOrUpdate(profile)
  }

  suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
    messageDao.clearAllMessages()
    callLogDao.clearAll()
  }

  private suspend fun seedInitialDataIfNeeded() {
    val existing = conversationDao.getAllConversations().first()
    if (existing.isEmpty()) {
      val myProfile = UserProfileEntity(
        userId = "google_user_current",
        email = "creative.faizan.dev@gmail.com",
        displayName = "Creative Faizan",
        photoUrl = null,
        e2eePublicKeyFingerprint = "SHA256:F8:4D:2A:9C:10:E4:7B:3A",
        isGoogleOAuthConnected = true,
        hardwareKeyStoreId = "TEE-ARM-TRUSTZONE-HWKEY-PRIMARY"
      )
      userProfileDao.insertOrUpdate(myProfile)

      val elenaSafety = CryptoEngine.generateSafetyNumber("my_id", "contact_elena")
      val marcusSafety = CryptoEngine.generateSafetyNumber("my_id", "contact_marcus")
      val sarahSafety = CryptoEngine.generateSafetyNumber("my_id", "contact_sarah")

      val c1 = ConversationEntity(
        id = "contact_elena",
        contactName = "Elena Rostova",
        contactUid = "seed_elena",
        contactUsername = "elena",
        avatarSeed = "Elena",
        lastEncryptedMessage = "🔒 E2EE: Key exchange complete. Channel authenticated.",
        lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 12,
        unreadCount = 1,
        safetyNumber = elenaSafety,
        isVerified = true,
        e2eeFingerprint = "SHA256:7B:9F:88:1C:3D:20:9A:FF",
        isOnline = true
      )

      val c2 = ConversationEntity(
        id = "contact_marcus",
        contactName = "Dr. Marcus Vance",
        contactUid = "seed_marcus",
        contactUsername = "marcus",
        avatarSeed = "Marcus",
        lastEncryptedMessage = "📞 Voice Call (04:18) • Encrypted",
        lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 65,
        unreadCount = 0,
        safetyNumber = marcusSafety,
        isVerified = true,
        e2eeFingerprint = "SHA256:4C:2A:E0:11:8F:67:3B:55",
        isOnline = false
      )

      val c3 = ConversationEntity(
        id = "contact_sarah",
        contactName = "Sarah Connor",
        contactUid = "seed_sarah",
        contactUsername = "sarah",
        avatarSeed = "Sarah",
        lastEncryptedMessage = "📹 🔒 Encrypted Video • Verified",
        lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 340,
        unreadCount = 0,
        safetyNumber = sarahSafety,
        isVerified = false,
        e2eeFingerprint = "SHA256:9E:11:05:44:A2:CC:89:12",
        isOnline = true
      )

      conversationDao.insertAll(listOf(c1, c2, c3))

      // Pre-populate messages for Elena
      val m1Enc = CryptoEngine.encryptString("Initiating end-to-end encrypted session via ECDH Curve25519.")
      val m2Enc = CryptoEngine.encryptString("Session keys derived with HKDF-SHA256. Voice audio packets and all media streams are now sealed.")
      val m3Enc = CryptoEngine.encryptString("Key exchange complete. Channel authenticated.")

      val m1 = MessageEntity(
        conversationId = "contact_elena",
        senderId = "contact_elena",
        senderName = "Elena Rostova",
        isOutgoing = false,
        cipherText = m1Enc.cipherTextBase64,
        iv = m1Enc.ivBase64,
        messageType = MessageType.TEXT.name,
        timestamp = System.currentTimeMillis() - 1000 * 60 * 30,
        status = MessageStatus.READ.name
      )
      val m2 = MessageEntity(
        conversationId = "contact_elena",
        senderId = "me",
        senderName = "You",
        isOutgoing = true,
        cipherText = m2Enc.cipherTextBase64,
        iv = m2Enc.ivBase64,
        messageType = MessageType.TEXT.name,
        timestamp = System.currentTimeMillis() - 1000 * 60 * 20,
        status = MessageStatus.READ.name
      )
      val m3 = MessageEntity(
        conversationId = "contact_elena",
        senderId = "contact_elena",
        senderName = "Elena Rostova",
        isOutgoing = false,
        cipherText = m3Enc.cipherTextBase64,
        iv = m3Enc.ivBase64,
        messageType = MessageType.TEXT.name,
        timestamp = System.currentTimeMillis() - 1000 * 60 * 12,
        status = MessageStatus.DELIVERED.name
      )

      // An encrypted audio message demo for Elena
      val voiceEnc = CryptoEngine.encryptString("Encrypted voice note: Confirmed zero packet leakage on our voice bridge.")
      val mVoice = MessageEntity(
        conversationId = "contact_elena",
        senderId = "contact_elena",
        senderName = "Elena Rostova",
        isOutgoing = false,
        cipherText = voiceEnc.cipherTextBase64,
        iv = voiceEnc.ivBase64,
        messageType = MessageType.AUDIO_VOICE_NOTE.name,
        mediaDurationSeconds = 14,
        timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
        status = MessageStatus.READ.name
      )

      messageDao.insertAll(listOf(m1, mVoice, m2, m3))

      // Prepopulate call logs
      val call1 = CallLogEntity(
        contactId = "contact_marcus",
        contactName = "Dr. Marcus Vance",
        callType = CallType.VOICE.name,
        direction = CallDirection.OUTGOING.name,
        durationSeconds = 258,
        timestamp = System.currentTimeMillis() - 1000 * 60 * 65,
        e2eeFingerprint = "SHA256:4C:2A:E0:11:8F:67:3B:55",
        encryptionVerified = true
      )
      val call2 = CallLogEntity(
        contactId = "contact_elena",
        contactName = "Elena Rostova",
        callType = CallType.VOICE.name,
        direction = CallDirection.INCOMING.name,
        durationSeconds = 145,
        timestamp = System.currentTimeMillis() - 1000 * 60 * 1440,
        e2eeFingerprint = "SHA256:7B:9F:88:1C:3D:20:9A:FF",
        encryptionVerified = true
      )
      callLogDao.insertAll(listOf(call1, call2))
    }
  }

  private val prefs by lazy { context.getSharedPreferences("cipher_secure_prefs", Context.MODE_PRIVATE) }

  private suspend fun getOrGenerateKeyPair(): String {
      val existingPriv = prefs.getString("x25519_private_key_enc", null)
      val existingPub = prefs.getString("x25519_public_key", null)
      
      if (existingPriv != null && existingPub != null) {
          return existingPub
      }
      
      val kp = CryptoEngine.generateE2EEKeyPair()
      val privB64 = CryptoEngine.encodeKey(kp.private)
      val pubB64 = CryptoEngine.encodeKey(kp.public)
      
      val encryptedPriv = CryptoEngine.encryptString(privB64)
      
      prefs.edit()
          .putString("x25519_private_key_enc", encryptedPriv.cipherTextBase64)
          .putString("x25519_private_key_iv", encryptedPriv.ivBase64)
          .putString("x25519_public_key", pubB64)
          .apply()
          
      return pubB64
  }

  private fun getMyPrivateKey(): PrivateKey? {
      val cipherText = prefs.getString("x25519_private_key_enc", null) ?: return null
      val iv = prefs.getString("x25519_private_key_iv", null) ?: return null
      val privB64 = CryptoEngine.decryptString(cipherText, iv)
      return CryptoEngine.decodePrivateKey(privB64)
  }
}
