sed -i '/^}$/d' app/src/main/java/com/example/crypto/CryptoEngine.kt

cat << 'INNER_EOF' >> app/src/main/java/com/example/crypto/CryptoEngine.kt

  fun generateE2EEKeyPair(): KeyPair {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
    }
    val kpg = KeyPairGenerator.getInstance("X25519", "BC")
    return kpg.generateKeyPair()
  }

  fun encodeKey(key: java.security.Key): String {
      return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
  }

  fun decodePublicKey(base64: String): PublicKey {
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
          Security.addProvider(BouncyCastleProvider())
      }
      val kf = KeyFactory.getInstance("X25519", "BC")
      return kf.generatePublic(X509EncodedKeySpec(Base64.decode(base64, Base64.NO_WRAP)))
  }

  fun decodePrivateKey(base64: String): PrivateKey {
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
          Security.addProvider(BouncyCastleProvider())
      }
      val kf = KeyFactory.getInstance("X25519", "BC")
      return kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(base64, Base64.NO_WRAP)))
  }

  fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): SecretKey {
      val ka = KeyAgreement.getInstance("X25519", "BC")
      ka.init(privateKey)
      ka.doPhase(publicKey, true)
      val sharedSecretBytes = ka.generateSecret()
      
      val md = MessageDigest.getInstance("SHA-256")
      val aesKeyBytes = md.digest(sharedSecretBytes)
      return SecretKeySpec(aesKeyBytes, "AES")
  }

  fun encryptStringE2EE(plainText: String, sharedKey: SecretKey): EncryptedResult {
      val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
      val cipher = Cipher.getInstance(TRANSFORMATION)
      val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
      cipher.init(Cipher.ENCRYPT_MODE, sharedKey, spec)
      val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
      return EncryptedResult(
          cipherTextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
          ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
          algorithm = "AES-256-GCM / X25519"
      )
  }

  fun decryptStringE2EE(cipherTextBase64: String, ivBase64: String, sharedKey: SecretKey): String {
      return try {
          val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
          val encryptedBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
          val cipher = Cipher.getInstance(TRANSFORMATION)
          val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
          cipher.init(Cipher.DECRYPT_MODE, sharedKey, spec)
          val decryptedBytes = cipher.doFinal(encryptedBytes)
          String(decryptedBytes, Charsets.UTF_8)
      } catch (e: Exception) {
          "[E2EE Decryption Failed]"
      }
  }
}
INNER_EOF
