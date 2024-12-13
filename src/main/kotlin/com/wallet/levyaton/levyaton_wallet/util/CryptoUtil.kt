package com.wallet.levyaton.levyaton_wallet.util

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.IvParameterSpec
import java.util.Base64
import java.security.SecureRandom

private const val SALT = "xoCw-{ea(o2j-Z!"
class CryptoUtil {

    companion object{
        fun encrypt(data: String, secret: String): String {
            // First layer: AES Encryption
            val aesEncrypted = aesEncrypt(data, secret)

            // Second layer: HMAC for integrity
            val hmacValue = hmac(aesEncrypted, secret)

            // Combine HMAC and AES encrypted data
            val combined = hmacValue + ":" + aesEncrypted

            // Third layer: Base64 Encoding
            return Base64.getEncoder().encodeToString(combined.toByteArray(Charsets.UTF_8))
        }

        // Decryption function corresponding to the encryption
        private fun decrypt(data: String, secret: String): String {
            // Third layer: Base64 Decoding
            val decodedData = String(Base64.getDecoder().decode(data), Charsets.UTF_8)

            // Split HMAC and AES encrypted data
            val parts = decodedData.split(":")
            if (parts.size != 2) throw IllegalArgumentException("Invalid data")

            val hmacValue = parts[0]
            val aesEncrypted = parts[1]

            // Verify HMAC
            val expectedHmac = hmac(aesEncrypted, secret)
            if (hmacValue != expectedHmac) throw IllegalArgumentException("Data integrity check failed")

            // First layer: AES Decryption
            return aesDecrypt(aesEncrypted, secret)
        }

        // AES Encryption
        private fun aesEncrypt(data: String, secret: String): String {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = generateKeySpec(secret)
            val iv = generateIv()
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val ivAndEncrypted = iv + encrypted
            return Base64.getEncoder().encodeToString(ivAndEncrypted)
        }

        // AES Decryption
        private fun aesDecrypt(data: String, secret: String): String {
            val dataBytes = Base64.getDecoder().decode(data)
            val iv = dataBytes.copyOfRange(0, 16)
            val encryptedData = dataBytes.copyOfRange(16, dataBytes.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = generateKeySpec(secret)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(encryptedData)
            return String(decrypted, Charsets.UTF_8)
        }

        // Generate SecretKeySpec for AES
        private fun generateKeySpec(secret: String): SecretKeySpec {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(secret.toCharArray(), SALT.toByteArray(), 65536, 256)
            val tmp = factory.generateSecret(spec)
            return SecretKeySpec(tmp.encoded, "AES")
        }

        private fun hmac(data: String, secret: String): String {
            val hmacKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(hmacKey)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(hmacBytes)
        }

        private fun generateIv(): ByteArray {
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            return iv
        }
    }


}

