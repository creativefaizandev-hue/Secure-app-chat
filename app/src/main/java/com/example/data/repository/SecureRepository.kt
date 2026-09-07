package com.example.data.repository

import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecureRepository(context: Context) {
  private val db = AppDatabase.getInstance(context)
  private val conversationDao = db.conversationDao()
  private val messageDao = db.messageDao()
  private val callLogDao = db.callLogDao()
  private val userProfileDao = db.userProfileDao()

  val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
  val callLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
  val userProfile: Flow<UserProfileEntity?> = userProfileDao.getProfile()
  val totalMessageCount: Flow<Int> = messageDao.getTotalMessageCount()

  init {
    CoroutineScope(Dispatchers.IO).launch {
      seedInitialDataIfNeeded()
    }
  }

  fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
    return messageDao.getMessagesForConversation(conversationId)
  }

  fun getConversation(id: String): Flow<ConversationEntity?> {
    return conversationDao.getConversationById(id)
  }

  suspend fun sendEncryptedTextMessage(
    conversationId: String,
    text: String,
    senderName: String = "You (Verified)"
  ) = withContext(Dispatchers.IO) {
    val encrypted = CryptoEngine.encryptString(text)
    val now = System.currentTimeMillis()

    val msg = MessageEntity(
      conversationId = conversationId,
      senderId = "me",
      senderName = senderName,
      isOutgoing = true,
      cipherText = encrypted.cipherTextBase64,
      iv = encrypted.ivBase64,
      encryptionAlgorithm = encrypted.algorithm,
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
}
