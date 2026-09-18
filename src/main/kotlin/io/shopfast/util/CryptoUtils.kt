package io.shopfast.util

import io.shopfast.config.AppProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utilitarios de criptografia do ShopFast.
 *
 * MD5, SHA-1, DES, ECB e o IV fixo sairam. O que ficou:
 *
 * - AES-256/GCM com IV sorteado por [SecureRandom] a cada chamada, entao o
 *   mesmo texto claro nunca gera o mesmo cifrado e a integridade vem junto,
 *   pela tag de autenticacao do proprio modo;
 * - HMAC-SHA256 para assinar tokens, comparado em tempo constante;
 * - a chave vem de [AppProperties], que a le de variavel de ambiente.
 *
 * Hash de senha nao mora mais aqui: quem cuida disso e o `PasswordEncoder`
 * (BCrypt) declarado em `io.shopfast.config.SecurityConfig`.
 */
@Component
class CryptoUtils(properties: AppProperties) {

    private val secretKey: SecretKey = deriveKey(properties.encryptionKey)

    private val random = SecureRandom()

    /** Cifra [plainText] com AES-256/GCM. O IV sorteado vai no inicio do resultado. */
    fun encrypt(plainText: String): String {
        val iv = ByteArray(GCM_IV_BYTES)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + cipherText)
    }

    /** Decifra o que [encrypt] produziu, separando o IV do corpo cifrado. */
    fun decrypt(cipherText: String): String {
        val decoded = Base64.getDecoder().decode(cipherText)
        require(decoded.size > GCM_IV_BYTES) { "Conteudo cifrado invalido" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = decoded.copyOfRange(0, GCM_IV_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(decoded, GCM_IV_BYTES, decoded.size - GCM_IV_BYTES)
        return String(plain, StandardCharsets.UTF_8)
    }

    /** Assinatura HMAC-SHA256 de [value], em hexadecimal. */
    fun hmacSha256(value: String, secret: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    /** Comparacao em tempo constante, para nao vazar o segredo por timing. */
    fun matchesSignature(expected: String, actual: String): Boolean = MessageDigest.isEqual(
        expected.toByteArray(StandardCharsets.UTF_8),
        actual.toByteArray(StandardCharsets.UTF_8),
    )

    private fun deriveKey(secret: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        return SecretKeySpec(digest.digest(secret.toByteArray(StandardCharsets.UTF_8)), "AES")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
