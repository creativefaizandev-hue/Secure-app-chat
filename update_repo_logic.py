import re

with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "r") as f:
    content = f.read()

# Update startFirestoreSync
sync_replacement = """  fun startFirestoreSync() {
    try {
      val currentUserId = auth.currentUser?.email ?: return
      if (isListeningToFirestore) return
      isListeningToFirestore = true
      
      CoroutineScope(Dispatchers.IO).launch {
          val pubKey = getOrGenerateKeyPair()
          firestore.collection("users").document(currentUserId).set(mapOf("publicKey" to pubKey)).await()
      }
      
      // Listen for new chats
"""
content = re.sub(r'  fun startFirestoreSync\(\) \{\n    try \{\n      val currentUserId = auth\.currentUser\?\.email \?: return\n      if \(isListeningToFirestore\) return\n      isListeningToFirestore = true\n      \n      // Listen for new chats', sync_replacement, content, count=1)

# Update sendEncryptedTextMessage
send_pattern = r'    val encrypted = CryptoEngine\.encryptString\(text\)\n    val now = System\.currentTimeMillis\(\)\n    val currentUserId = try \{ auth\.currentUser\?\.email \?: "me" \} catch \(e: Exception\) \{ "me" \}'
send_replacement = """    val now = System.currentTimeMillis()
    val currentUserId = try { auth.currentUser?.email ?: "me" } catch (e: Exception) { "me" }
    
    // Fetch recipient's public key
    val userDoc = firestore.collection("users").document(conv.contactEmail).get().await()
    val recipientPubB64 = userDoc.getString("publicKey")
    if (recipientPubB64 == null) {
        Log.e("SecureRepository", "Recipient has no public key published.")
        return@withContext
    }
    
    val myPriv = getMyPrivateKey() ?: return@withContext
    val recipientPub = CryptoEngine.decodePublicKey(recipientPubB64)
    val sharedSecret = CryptoEngine.deriveSharedSecret(myPriv, recipientPub)
    
    // Encrypt for wire (E2EE)
    val encrypted = CryptoEngine.encryptStringE2EE(text, sharedSecret)
    // Encrypt for local storage (Room)
    val localEncrypted = CryptoEngine.encryptString(text)
"""
# Wait, I need to find where conv is available. conv is inside `let` later. So I should fetch recipient's public key early.
# Let's do a more precise replacement script.
