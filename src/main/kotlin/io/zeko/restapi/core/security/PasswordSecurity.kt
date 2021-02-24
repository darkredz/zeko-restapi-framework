package io.zeko.restapi.core.security

import java.util.Base64
import java.math.BigInteger
import java.net.URLEncoder
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


class PasswordSecurity {

    fun hmacSha1(secretKey: String, rurl: String): String? {
        try {
            val mac = Mac.getInstance("HmacSHA1")
            val secret = SecretKeySpec(secretKey.toByteArray(), "HmacSHA1")
            mac.init(secret)
            val digest = mac.doFinal(rurl.toByteArray())
            val signature = Base64.getEncoder().encodeToString(digest)
            return URLEncoder.encode(signature, "UTF-8")
        } catch (err: Exception) {
        }
        return null
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun generatePasswordHash(password: String, iterations: Int = 3): String {
        val chars = password.toCharArray()
        val salt = salt.toByteArray()
        val spec = PBEKeySpec(chars, salt, iterations, 64 * 8)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val hash = skf.generateSecret(spec).encoded
        return iterations.toString() + ":" + toHex(salt) + ":" + toHex(hash)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun validatePassword(originalPassword: String, storedPassword: String): Boolean {
        val parts = storedPassword.split(":".toRegex()).toTypedArray()
        val iterations = parts[0].toInt()
        val salt = fromHex(parts[1])
        val hash = fromHex(parts[2])
        val spec = PBEKeySpec(originalPassword.toCharArray(), salt, iterations, 64 * 8)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val testHash = skf.generateSecret(spec).encoded
        return toHex(testHash) == toHex(hash)
    }

    @get:Throws(NoSuchAlgorithmException::class)
    val salt: String
        get() {
            val sr = SecureRandom.getInstance("SHA1PRNG")
            val salt = ByteArray(16)
            sr.nextBytes(salt)
            return salt.toString()
        }

    @get:Throws(NoSuchAlgorithmException::class)
    val saltAsString: String
        get() {
            val sr = SecureRandom.getInstance("SHA1PRNG")
            val salt = ByteArray(16)
            sr.nextBytes(salt)
            return toHex(salt.toString().toByteArray())
        }

    @Throws(NoSuchAlgorithmException::class)
    private fun toHex(array: ByteArray): String {
        val bi = BigInteger(1, array)
        val hex = bi.toString(16)
        val paddingLength = array.size * 2 - hex.length
        return if (paddingLength > 0) {
            String.format("%0" + paddingLength + "d", 0) + hex
        } else {
            hex
        }
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun fromHex(hex: String): ByteArray {
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            bytes[i] = hex.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        }
        return bytes
    }
}
