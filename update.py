import re

with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "r") as f:
    content = f.read()

# Replace startFirestoreSync
sync_from = """  fun startFirestoreSync() {
    try {
      val currentUserId = auth.currentUser?.email ?: return
      if (isListeningToFirestore) return
      isListeningToFirestore = true
      
      // Listen for new chats"""
sync_to = """  fun startFirestoreSync() {
    try {
      val currentUserId = auth.currentUser?.email ?: return
      if (isListeningToFirestore) return
      isListeningToFirestore = true
      
      CoroutineScope(Dispatchers.IO).launch {
          try {
              val pubKey = getOrGenerateKeyPair()
              firestore.collection("users").document(currentUserId).set(mapOf("publicKey" to pubKey)).await()
          } catch(e: Exception) { e.printStackTrace() }
      }
      
      // Listen for new chats"""
content = content.replace(sync_from, sync_to)

# Replace listenForMessages
listen_from = """                // For Phase 2, we encrypt the plaintext received from Firestore to store it locally
                // because the local UI expects cipherText.
                val encrypted = CryptoEngine.encryptString(text)
                
                // Avoid duplicate insert by checking if a message with this exact timestamp exists from this sender
                val existing = messageDao.getMessagesForConversation(chatId).first()
                if (existing.none { it.timestamp == timestamp && it.senderId == senderId }) {
                  val msg = MessageEntity(
                    conversationId = chatId,
                    senderId = senderId,
                    senderName = senderId.substringBefore("@"),
                    isOutgoing = false,
                    cipherText = encrypted.cipherTextBase64,
                    iv = encrypted.ivBase64,
                    encryptionAlgorithm = encrypted.algorithm,"""
listen_to = """                val cipherText = doc.getString("cipherText")
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
                    encryptionAlgorithm = "AES-256-GCM / X25519", """
content = content.replace(listen_from, listen_to)

# Replace sendEncryptedTextMessage
send_from = """    val encrypted = CryptoEngine.encryptString(text)
    val now = System.currentTimeMillis()
    val currentUserId = try { auth.currentUser?.email ?: "me" } catch (e: Exception) { "me" }

    val msg = MessageEntity(
      conversationId = conversationId,
      senderId = currentUserId,
      senderName = senderName,
      isOutgoing = true,
      cipherText = encrypted.cipherTextBase64,
      iv = encrypted.ivBase64,
      encryptionAlgorithm = encrypted.algorithm,"""

send_to = """    val now = System.currentTimeMillis()
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
      encryptionAlgorithm = algo,"""
content = content.replace(send_from, send_to)

# Replace the inner try block in sendEncryptedTextMessage
send_inner_from = """      // Phase 2: Send plaintext to Firestore
      try {
        val chatRef = firestore.collection("chats").document(conversationId)
        
        // Ensure chat document exists with participants
        chatRef.set(mapOf("participants" to listOf(currentUserId, conv.contactEmail))).await()
        
        // Add message
        val messageMap = mapOf(
          "senderId" to currentUserId,
          "text" to text, // PLAINTEXT for Phase 2
          "timestamp" to now,
          "messageType" to MessageType.TEXT.name
        )
        chatRef.collection("messages").add(messageMap).await()
      } catch (e: Exception) {"""
send_inner_to = """      try {
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
      } catch (e: Exception) {"""
content = content.replace(send_inner_from, send_inner_to)

with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "w") as f:
    f.write(content)
