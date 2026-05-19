package com.corgimemo.app.backup.crypto

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密管理器
 * 使用 AES-256-GCM 算法进行加密和解密
 */
object EncryptionManager {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_SIZE = 256
    private const val ITERATION_COUNT = 100000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    /**
     * 加密数据
     *
     * @param data 要加密的数据
     * @param password 密码
     * @return 加密后的数据（格式：salt(16) + iv(12) + 密文 + tag(16)）
     * @throws Exception 如果加密失败
     */
    fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = generateRandomBytes(SALT_SIZE)
        val iv = generateRandomBytes(IV_SIZE)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val encrypted = cipher.doFinal(data)

        val result = ByteArray(salt.size + iv.size + encrypted.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(encrypted, 0, result, salt.size + iv.size, encrypted.size)

        return result
    }

    /**
     * 解密数据
     *
     * @param encryptedData 加密后的数据
     * @param password 密码
     * @return 解密后的数据
     * @throws Exception 如果解密失败（如密码错误）
     */
    fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        if (encryptedData.size < SALT_SIZE + IV_SIZE) {
            throw IllegalArgumentException("加密数据格式不正确")
        }

        val salt = ByteArray(SALT_SIZE)
        val iv = ByteArray(IV_SIZE)
        val encrypted = ByteArray(encryptedData.size - SALT_SIZE - IV_SIZE)

        System.arraycopy(encryptedData, 0, salt, 0, SALT_SIZE)
        System.arraycopy(encryptedData, SALT_SIZE, iv, 0, IV_SIZE)
        System.arraycopy(encryptedData, SALT_SIZE + IV_SIZE, encrypted, 0, encrypted.size)

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(encrypted)
    }

    /**
     * 加密字符串为 Base64 格式
     *
     * @param text 要加密的字符串
     * @param password 密码
     * @return Base64 编码的加密字符串
     * @throws Exception 如果加密失败
     */
    fun encryptToBase64(text: String, password: String): String {
        val encrypted = encrypt(text.toByteArray(Charsets.UTF_8), password)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * 解密 Base64 格式的字符串
     *
     * @param encryptedText Base64 编码的加密字符串
     * @param password 密码
     * @return 解密后的字符串
     * @throws Exception 如果解密失败
     */
    fun decryptFromBase64(encryptedText: String, password: String): String {
        val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
        val decrypted = decrypt(encrypted, password)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * 从密码派生密钥
     * 使用 PBKDF2 算法
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec: KeySpec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATION_COUNT,
            KEY_SIZE
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    /**
     * 生成随机字节
     */
    private fun generateRandomBytes(size: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }
}
