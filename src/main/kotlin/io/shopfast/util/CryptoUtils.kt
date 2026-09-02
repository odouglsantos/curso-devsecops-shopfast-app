package io.shopfast.util

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Utilitarios de criptografia do ShopFast.
 *
 * Concentra os achados de criptografia fraca das Aulas 2.4 e 2.6:
 *
 * - VULN (kotlin:S4790): MD5 e SHA-1 usados para hash de senha. Sao quebraveis
 *   por rainbow table em segundos e nao tem fator de trabalho;
 * - VULN (kotlin:S5547): DES, com chave efetiva de 56 bits, quebrado por forca
 *   bruta desde os anos 90;
 * - VULN (kotlin:S5542): modo ECB, que preserva padroes do texto claro, e modo
 *   CBC com IV fixo, que torna a cifragem deterministica;
 * - VULN (kotlin:S6418): a chave de cifragem mora no proprio codigo.
 */
object CryptoUtils {

    /** VULN (kotlin:S6418): chave simetrica hardcoded. */
    const val SECRET_KEY = "ShopFastKey12345"

    /** VULN (kotlin:S6418 / kotlin:S5547): chave DES de 8 bytes no codigo. */
    private const val DES_KEY = "shopfast"

    /** VULN (kotlin:S5542): IV fixo — a mesma entrada gera sempre o mesmo cifrado. */
    private val FIXED_IV = "1234567890123456".toByteArray()

    /** VULN (kotlin:S4790): MD5 para senha. */
    fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** VULN (kotlin:S4790): SHA-1 tambem esta quebrado para uso de seguranca. */
    fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** VULN (kotlin:S4790): senha guardada em MD5 sem sal. */
    fun hashPassword(password: String): String = md5(password)

    /** VULN (kotlin:S5547 + kotlin:S5542): DES em modo ECB. */
    fun encryptDes(plainText: String): String {
        val key = SecretKeySpec(DES_KEY.toByteArray(), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray()))
    }

    /** VULN (kotlin:S5542): AES em modo ECB, que vaza padroes do texto claro. */
    fun encryptEcb(plainText: String): String {
        val key = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray()))
    }

    /** VULN (kotlin:S5542): AES/CBC com IV fixo — cifragem deterministica. */
    fun encryptCbc(plainText: String): String {
        val key = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(FIXED_IV))
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray()))
    }

    fun decryptCbc(cipherText: String): String {
        val key = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(FIXED_IV))
        return String(cipher.doFinal(Base64.getDecoder().decode(cipherText)))
    }
}
