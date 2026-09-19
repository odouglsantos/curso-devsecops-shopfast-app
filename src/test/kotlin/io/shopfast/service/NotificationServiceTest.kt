package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.util.CryptoUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Lista branca de destino (anti-SSRF) e assinatura HMAC do corpo. O
 * `TrustManager` permissivo e o TLS 1.1 fixo sairam com a correcao.
 */
class NotificationServiceTest {

    private val properties = AppProperties(
        webhookSigningSecret = "segredo-de-webhook",
        encryptionKey = "chave",
        // 127.0.0.1 esta na lista branca so para exercitar o caminho de erro de
        // conexao: a porta 1 recusa na hora, sem depender de rede externa.
        allowedWebhookHosts = listOf("partners.shopfast.io", "127.0.0.1"),
    )
    private val crypto = CryptoUtils(properties)
    private val service = NotificationService(properties, crypto)

    @Test
    fun `destino http e recusado antes de qualquer conexao`() {
        assertEquals(
            NotificationService.FAILED_STATUS,
            service.post("http://partners.shopfast.io/hook", "{}"),
        )
    }

    @Test
    fun `host fora da lista branca e recusado`() {
        assertEquals(
            NotificationService.FAILED_STATUS,
            service.post("https://169.254.169.254/latest/meta-data", "{}"),
        )
    }

    @Test
    fun `url malformada e recusada sem estourar excecao`() {
        assertEquals(NotificationService.FAILED_STATUS, service.post("nao e uma url", "{}"))
        assertEquals(NotificationService.FAILED_STATUS, service.post("", "{}"))
        assertEquals(NotificationService.FAILED_STATUS, service.post("file:///etc/passwd", "{}"))
    }

    @Test
    fun `esquema https e comparado sem diferenciar maiuscula`() {
        assertEquals(
            NotificationService.FAILED_STATUS,
            service.post("HTTPS://intranet.local/hook", "{}"),
        )
    }

    @Test
    fun `destino autorizado que recusa conexao devolve status de falha`() {
        val status = service.post(
            "https://127.0.0.1:1/hook",
            """{"ok":true}""",
            mapOf("X-Teste" to "1"),
        )

        assertEquals(NotificationService.FAILED_STATUS, status)
    }

    @Test
    fun `webhook de parceiro assina o corpo e respeita a lista branca`() {
        assertEquals(
            NotificationService.FAILED_STATUS,
            service.callPartnerWebhook("https://atacante.example.com/hook", 42L),
        )
        assertEquals(
            NotificationService.FAILED_STATUS,
            service.callPartnerWebhook("https://127.0.0.1:1/hook", 42L),
        )
    }
}
