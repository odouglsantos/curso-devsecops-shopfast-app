package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.util.CryptoUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI

/**
 * Disparo de webhooks e notificacoes para sistemas parceiros.
 *
 * - a validacao de TLS da JVM voltou a valer: o `TrustManager` que aceitava
 *   qualquer certificado e o `HostnameVerifier` sempre verdadeiro sairam, assim
 *   como o `SSLContext` preso em TLSv1.1;
 * - o segredo de assinatura vem de configuracao e nao viaja em claro: o que vai
 *   no cabecalho e o HMAC-SHA256 do corpo;
 * - **SSRF fechado**: o destino passa por lista branca de host e so aceita
 *   HTTPS, entao nao da mais para apontar o webhook para `169.254.169.254` ou
 *   para um servico interno.
 */
@Service
class NotificationService(
    private val properties: AppProperties,
    private val cryptoUtils: CryptoUtils,
) {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /** Devolve o status HTTP, ou [FAILED_STATUS] quando a chamada nao completa. */
    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): Int {
        if (!isAllowed(url)) {
            logger.warn("Destino de notificacao recusado pela lista branca")
            return FAILED_STATUS
        }
        return try {
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode
        } catch (e: Exception) {
            logger.error("Falha ao notificar destino externo", e)
            FAILED_STATUS
        }
    }

    fun callPartnerWebhook(partnerUrl: String, orderId: Long): Int {
        val body = """{"orderId":$orderId}"""
        return post(
            partnerUrl,
            body,
            mapOf("X-Signature" to cryptoUtils.hmacSha256(body, properties.webhookSigningSecret)),
        )
    }

    /**
     * Só HTTPS e só host declarado em `shopfast.allowed-webhook-hosts`. Redirect
     * fica desligado no [post] para o destino nao ser trocado depois do aceite.
     */
    private fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI.create(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host != null &&
            uri.host in properties.allowedWebhookHosts
    }.getOrDefault(false)

    companion object {
        const val FAILED_STATUS = -1
        private const val TIMEOUT_MS = 5_000
    }
}
