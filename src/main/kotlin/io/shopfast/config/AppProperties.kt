package io.shopfast.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.SecureRandom
import java.util.Base64

/**
 * Parametros de integracao do ShopFast.
 *
 * Nada de segredo, credencial de banco ou IP de infraestrutura no codigo: as
 * chaves vem de variavel de ambiente e, quando a variavel nao existe, um valor
 * aleatorio e sorteado na subida — o laboratorio sobe sem que exista segredo
 * versionado no repositorio.
 *
 * [allowedOrigins] e [allowedWebhookHosts] sao listas brancas: a primeira fecha
 * o CORS, a segunda fecha o SSRF do disparo de webhooks.
 */
@ConfigurationProperties(prefix = "shopfast")
class AppProperties(
    jwtSecret: String = "",
    paymentApiKey: String = "",
    encryptionKey: String = "",
    webhookSigningSecret: String = "",
    val billingBaseUrl: String = "https://billing.internal.shopfast.io",
    val partnerWebhook: String = "https://partners.shopfast.io/webhooks/orders",
    val reportDirectory: String = "/var/shopfast/reports",
    /** Origens autorizadas a chamar a API pelo navegador. Vazio = nenhuma. */
    val allowedOrigins: List<String> = emptyList(),
    /** Hosts para os quais o ShopFast aceita disparar webhook. */
    val allowedWebhookHosts: List<String> = emptyList(),
) {

    /** Chave de assinatura HMAC dos tokens de sessao. */
    val jwtSecret: String = jwtSecret.ifBlank { randomSecret() }

    /** Credencial do gateway de pagamento. */
    val paymentApiKey: String = paymentApiKey.ifBlank { randomSecret() }

    /** Chave usada para derivar a chave AES de dados sensiveis. */
    val encryptionKey: String = encryptionKey.ifBlank { randomSecret() }

    /** Segredo de assinatura dos webhooks enviados a parceiros. */
    val webhookSigningSecret: String = webhookSigningSecret.ifBlank { randomSecret() }

    private companion object {
        private const val SECRET_BYTES = 32
        private val RANDOM = SecureRandom()

        fun randomSecret(): String {
            val bytes = ByteArray(SECRET_BYTES)
            RANDOM.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
