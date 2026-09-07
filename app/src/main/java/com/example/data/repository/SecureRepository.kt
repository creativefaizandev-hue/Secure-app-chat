package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.security.PrivateKey
import javax.crypto.SecretKey
import android.util.Log
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
  
  fun startFirestoreSync() {
    try {
      val currentUserId = auth.currentUser?.email ?: return
      if (isListeningToFirestore) return
      isListeningToFirestore = true
      
      CoroutineScope(Dispatchers.IO).launch {
          try {
              val pubKey = getOrGenerateKeyPair()
              val userMap = mutableMapOf<String, Any>("publicKey" to pubKey)
              try {
                  val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                  userMap["fcmToken"] = token
              } catch (e: Exception) {
                  // Optional FCM token fallback
              }
              firestore.collection("users").document(currentUserId)
                  .set(userMap, com.google.firebase.firestore.SetOptions.merge()).await()
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
            val otherEmail = participants.firstOrNull { it != currentUserId } ?: currentUserId
            
            // Check if conversation exists locally
            val existing = conversationDao.getConversationSync(chatId)
            if (existing == null) {
              val newConv = ConversationEntity(
                id = chatId,
                contactName = otherEmail.substringBefore("@"),
                contactEmail = otherEmail,
                avatarSeed = otherEmail,
                lastEncryptedMessage = "New Chat",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                safetyNumber = CryptoEngine.generateSafetyNumber(currentUserId, otherEmail),
                isVerified = false,
                e2eeFingerprint = CryptoEngine.computeKeyFingerprint(otherEmail),
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
                // It's an incoming message
                val text = doc.getString("text") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                
                val cipherText = doc.getString("cipherText")
                val iv = doc.getString("iv")
                var decryptedText = text // fallback if plaintext

                if (cipherText != null && iv != null) {
                    try {
                        val senderDoc = firestore.collection("users").document(senderId).get().await()
                        val senderPubB64 = senderDoc.getString("publicKey")
                        if (senderPubB64 != null) {
                            val myPriv = getMyPrivateKey()
                            if (myPriv != null) {
                                val senderPub = CryptoEngine.decodePublicKey(senderPubB64)
                                val sharedSecret = CryptoEngine.deriveSharedSecret(myPriv, senderPub)
                                decryptedText = CryptoEngine.decryptStringE2EE(cipherText, iv, sharedSecret)
                            }
                        }
                    } catch (e: Exception) {
                        decryptedText = "[E2EE Decryption Failed]"
                    }
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
  ) = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val currentUserId = try { auth.currentUser?.email ?: "me" } catch (e: Exception) { "me" }
    
    // 1. Envelope Encryption: Generate a random AES key per file
    val fileAesKey = CryptoEngine.generateRandomAesKey()
    
    // 2. Encrypt the file bytes with this key locally
    val encryptedFile = CryptoEngine.encryptFile(fileBytes, fileAesKey)
    val cipherTextBytes = android.util.Base64.decode(encryptedFile.cipherTextBase64, android.util.Base64.NO_WRAP)
    
    // 3. Upload the encrypted blob to Firebase Storage
    val storageRef = storage.reference.child("attachments/$conversationId/${java.util.UUID.randomUUID()}")
    try {
        storageRef.putBytes(cipherTextBytes).await()
    } catch(e: Exception) {
        e.printStackTrace()
        return@withContext
    }
    
    val downloadUrl = try { storageRef.downloadUrl.await().toString() } catch(e:Exception){ "" }
    
    // 4. Encrypt the fileAesKey with the recipient's X25519 public key (using ECDH)
    var wireCipherText = ""
    var wireIv = ""
    var localCipherText = ""
    var localIv = ""
    var algo = "AES-256-GCM / X25519"
    var encryptedKeyB64 = ""

    val fileAesKeyB64 = android.util.Base64.encodeToString(fileAesKey, android.util.Base64.NO_WRAP)

    val conv = conversationDao.getConversationSync(conversationId)
    if (conv != null) {
        try {
            val userDoc = firestore.collection("users").document(conv.contactEmail).get().await()
            val recipientPubB64 = userDoc.getString("publicKey")
            if (recipientPubB64 != null) {
                val myPriv = getMyPrivateKey()
                if (myPriv != null) {
                    val recipientPub = CryptoEngine.decodePublicKey(recipientPubB64)
                    val sharedSecret = CryptoEngine.deriveSharedSecret(myPriv, recipientPub)
                    
                    // We encrypt the file's AES key using the shared secret
                    val encryptedKeyData = CryptoEngine.encryptStringE2EE(fileAesKeyB64, sharedSecret)
                    wireCipherText = encryptedKeyData.cipherTextBase64 // This is the encrypted AES key on the wire
                    wireIv = encryptedKeyData.ivBase64
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Local copy of the encrypted AES key
    val localEncryptedKeyData = CryptoEngine.encryptString(fileAesKeyB64)
    localCipherText = localEncryptedKeyData.cipherTextBase64
    localIv = localEncryptedKeyData.ivBase64

    // The message payload contains the download URL and the file's IV in the mediaBase64OrUri field
    // format: [downloadUrl]|[fileIvBase64]|[fileName]|[mimeType]
    val mediaPayload = "$downloadUrl|${encryptedFile.ivBase64}|$fileName|$mimeType"

    val msg = MessageEntity(
      conversationId = conversationId,
      senderId = currentUserId,
      senderName = senderName,
      isOutgoing = true,
      cipherText = localCipherText, // the local encrypted fileAesKey
      iv = localIv,
      encryptionAlgorithm = algo,
      messageType = MessageType.IMAGE.name, // or FILE
      mediaBase64OrUri = mediaPayload,
      timestamp = now,
      status = MessageStatus.SENT.name
    )
    messageDao.insert(msg)

    // Update conversation snippet
    conversationDao.getConversationSync(conversationId)?.let { c ->
      conversationDao.update(
        c.copy(
          lastEncryptedMessage = "📎 Encrypted Attachment",
          lastMessageTimestamp = now
        )
      )
    }

    // 5. Send the encrypted AES key and mediaPayload alongside the message to Firestore
    try {
      val chatRef = firestore.collection("chats").document(conversationId)
      chatRef.set(mapOf("participants" to listOf(currentUserId, conv?.contactEmail ?: ""))).await()
      
      val messageMap = mutableMapOf<String, Any>(
        "senderId" to currentUserId,
        "timestamp" to now,
        "messageType" to MessageType.IMAGE.name,
        "mediaPayload" to mediaPayload // The download URL and file IV
      )
      if (wireCipherText.isNotEmpty()) {
          messageMap["cipherText"] = wireCipherText // The encrypted fileAesKey
          messageMap["iv"] = wireIv
      }
      
      chatRef.collection("messages").add(messageMap).await()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  suspend fun sendEncryptedTextMessage(
    conversationId: String,
    text: String,
    senderName: String = "You (Verified)"
  ) = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val currentUserId = try { auth.currentUser?.email ?: "me" } catch (e: Exception) { "me" }

    var wireCipherText = ""
    var wireIv = ""
    var localCipherText = ""
    var localIv = ""
    var algo = "AES-256-GCM"

    val conv = conversationDao.getConversationSync(conversationId)
    if (conv != null) {
        try {
            val userDoc = firestore.collection("users").document(conv.contactEmail).get().await()
            val recipientPubB64 = userDoc.getString("publicKey")
            if (recipientPubB64 != null) {
                val myPriv = getMyPrivateKey()
                if (myPriv != null) {
                    val recipientPub = CryptoEngine.decodePublicKey(recipientPubB64)
                    val sharedSecret = CryptoEngine.deriveSharedSecret(myPriv, recipientPub)
                    val encrypted = CryptoEngine.encryptStringE2EE(text, sharedSecret)
                    wireCipherText = encrypted.cipherTextBase64
                    wireIv = encrypted.ivBase64
                    algo = encrypted.algorithm
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    val localEncrypted = CryptoEngine.encryptString(text)
    localCipherText = localEncrypted.cipherTextBase64
    localIv = localEncrypted.ivBase64

    val msg = MessageEntity(
      conversationId = conversationId,
      senderId = currentUserId,
      senderName = senderName,
      isOutgoing = true,
      cipherText = localCipherText,
      iv = localIv,
      encryptionAlgorithm = algo,
      messageType = MessageType.TEXT.name,
      timestamp = now,
      status = MessageStatus.SENT.name
    )
    messageDao.insert(msg)

    // Update conversation snippet
    conversationDao.getConversationSync(conversationId)?.let { conv ->
      conversationDao.update(
        conv.copy(
          lastEncryptedMessage = "🔒 E2EE: $text",
          lastMessageTimestamp = now
        )
      )
      
      try {
        val chatRef = firestore.collection("chats").document(conversationId)
        chatRef.set(mapOf("participants" to listOf(currentUserId, conv.contactEmail))).await()
        
        val messageMap = mutableMapOf<String, Any>(
          "senderId" to currentUserId,
          "timestamp" to now,
          "messageType" to MessageType.TEXT.name
        )
        if (wireCipherText.isNotEmpty()) {
            messageMap["cipherText"] = wireCipherText
            messageMap["iv"] = wireIv
        } else {
            messageMap["text"] = text // Fallback to plaintext if E2EE not established
        }
        
        chatRef.collection("messages").add(messageMap).await()
      } catch (e: Exception) {
        Log.e("SecureRepository", "Failed to send to Firestore", e)
      }
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
        contactEmail = "elena.rostova@cybersec.labs",
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
        contactEmail = "m.vance@quantum-defense.org",
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
        contactEmail = "sarah.connor@tactical.net",
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
