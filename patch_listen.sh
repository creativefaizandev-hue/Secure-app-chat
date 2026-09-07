cat << 'PY' > patch.py
with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "r") as f:
    text = f.read()

listen_from = """                if (cipherText != null && iv != null) {
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
                    status = MessageStatus.RECEIVED.name
                  )"""

listen_to = """                val msgTypeStr = doc.getString("messageType") ?: MessageType.TEXT.name
                val mediaPayload = doc.getString("mediaPayload")

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
                    cipherText = encrypted.cipherTextBase64, // For TEXT: the actual text. For IMAGE: the fileAesKey
                    iv = encrypted.ivBase64,
                    encryptionAlgorithm = "AES-256-GCM / X25519", 
                    messageType = msgTypeStr,
                    mediaBase64OrUri = mediaPayload,
                    timestamp = timestamp,
                    status = MessageStatus.RECEIVED.name
                  )"""

text = text.replace(listen_from, listen_to)

with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "w") as f:
    f.write(text)
PY
python3 patch.py
