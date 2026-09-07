cat << 'PY' > patch.py
with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "r") as f:
    text = f.read()

# Add sendEncryptedAttachmentMessage
attachment_func = """
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
"""

if "sendEncryptedAttachmentMessage" not in text:
    text = text.replace("suspend fun sendEncryptedTextMessage", attachment_func + "\n  suspend fun sendEncryptedTextMessage")

with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "w") as f:
    f.write(text)
PY
python3 patch.py
