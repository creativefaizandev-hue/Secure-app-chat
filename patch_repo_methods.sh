sed -i '/^}$/d' app/src/main/java/com/example/data/repository/SecureRepository.kt

cat << 'INNER_EOF' >> app/src/main/java/com/example/data/repository/SecureRepository.kt

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
INNER_EOF
