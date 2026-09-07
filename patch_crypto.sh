cat << 'PY' > patch.py
with open("app/src/main/java/com/example/crypto/CryptoEngine.kt", "r") as f:
    text = f.read()

helpers = """
    fun generateRandomAesKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGen.init(256)
        return keyGen.generateKey().encoded
    }

    fun encryptFile(fileBytes: ByteArray, aesKey: ByteArray): EncryptedData {
        val secretKey = javax.crypto.spec.SecretKeySpec(aesKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(fileBytes)
        return EncryptedData(
            cipherTextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            algorithm = "AES-256-GCM"
        )
    }

    fun decryptFile(cipherTextBase64: String, ivBase64: String, aesKey: ByteArray): ByteArray {
        val cipherText = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val secretKey = javax.crypto.spec.SecretKeySpec(aesKey, "AES")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(cipherText)
    }
"""

if "generateRandomAesKey" not in text:
    text = text.replace("object CryptoEngine {", "object CryptoEngine {" + helpers)

with open("app/src/main/java/com/example/crypto/CryptoEngine.kt", "w") as f:
    f.write(text)
PY
python3 patch.py
