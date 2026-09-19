package io.shopfast.util

import io.shopfast.config.AppProperties
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * AES-256/GCM com IV sorteado, HMAC-SHA256 e comparacao em tempo constante —
 * o que substituiu MD5, DES e ECB.
 */
class CryptoUtilsTest {

    private val crypto = CryptoUtils(AppProperties(encryptionKey = "chave-de-laboratorio"))

    @Test
    fun `texto cifrado volta ao original`() {
        val claro = "4111111111111111"

        assertEquals(claro, crypto.decrypt(crypto.encrypt(claro)))
    }

    @Test
    fun `mesmo texto gera cifrados diferentes porque o IV e sorteado`() {
        val primeiro = crypto.encrypt("segredo")
        val segundo = crypto.encrypt("segredo")

        assertNotEquals(primeiro, segundo)
        assertEquals("segredo", crypto.decrypt(primeiro))
        assertEquals("segredo", crypto.decrypt(segundo))
    }

    @Test
    fun `texto vazio e acentuacao sobrevivem ao ciclo`() {
        assertEquals("", crypto.decrypt(crypto.encrypt("")))
        assertEquals("cartao do Joao", crypto.decrypt(crypto.encrypt("cartao do Joao")))
    }

    @Test
    fun `conteudo cifrado curto demais e recusado`() {
        val curto = Base64.getEncoder().encodeToString(ByteArray(4))

        assertFailsWith<IllegalArgumentException> { crypto.decrypt(curto) }
    }

    @Test
    fun `hmac e estavel para o mesmo par valor-segredo`() {
        val assinatura = crypto.hmacSha256("payload", "segredo")

        assertEquals(assinatura, crypto.hmacSha256("payload", "segredo"))
        assertEquals(64, assinatura.length)
        assertTrue(assinatura.all { it in "0123456789abcdef" })
    }

    @Test
    fun `hmac muda quando o segredo muda`() {
        assertNotEquals(
            crypto.hmacSha256("payload", "segredo-a"),
            crypto.hmacSha256("payload", "segredo-b"),
        )
    }

    @Test
    fun `comparacao de assinatura aceita igual e recusa diferente`() {
        assertTrue(crypto.matchesSignature("abc123", "abc123"))
        assertFalse(crypto.matchesSignature("abc123", "abc124"))
        assertFalse(crypto.matchesSignature("abc123", "abc1230"))
    }

    @Test
    fun `chave de cifra diferente nao decifra o conteudo alheio`() {
        val outro = CryptoUtils(AppProperties(encryptionKey = "outra-chave"))

        assertFailsWith<Exception> { outro.decrypt(crypto.encrypt("segredo")) }
    }
}
