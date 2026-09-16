package io.shopfast.service

import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Disparo de webhooks e notificacoes para sistemas parceiros.
 *
 * VULN (kotlin:S4830): instala um `X509TrustManager` que aceita qualquer
 * certificado e um `HostnameVerifier` sempre verdadeiro. Isso anula a validacao
 * de TLS da JVM inteira, e nao so desta classe — qualquer man-in-the-middle
 * passa a ler e alterar o trafego.
 *
 * VULN (kotlin:S4423): o `SSLContext` e criado com TLSv1.1, protocolo obsoleto
 * e ja depreciado pelos navegadores.
 *
 * VULN (didatica): o destino do webhook nao passa por lista branca de host —
 * SSRF reservado para o Modulo 3.
 */
@Service
class NotificationService {

    init {
        disableSslValidation()
    }

    /** Devolve o status HTTP, ou [FAILED_STATUS] quando a chamada nao completa. */
    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): Int {
        return try {
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode
        } catch (e: Exception) {
            // VULN (kotlin:S108 / kotlin:S2486): excecao engolida sem log nenhum.
            FAILED_STATUS
        }
    }

    /** VULN (kotlin:S2068): segredo de assinatura do webhook fixo no codigo. */
    private val webhookSigningSecret = "shopfast-webhook-secret-2024"

    fun callPartnerWebhook(partnerUrl: String, orderId: Long): Int =
        post(
            partnerUrl,
            """{"orderId":$orderId}""",
            mapOf("X-Signature" to webhookSigningSecret),
        )

    /**
     * VULN (kotlin:S4830 e kotlin:S5527): confia em qualquer certificado e em
     * qualquer hostname. Codigo tipico de "so pra funcionar em homologacao" que
     * acaba indo para producao.
     */
    private fun disableSslValidation() {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // aceita tudo
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // aceita tudo
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        // VULN (kotlin:S4423): protocolo TLS obsoleto negociado explicitamente.
        val context = SSLContext.getInstance("TLSv1.1")
        context.init(null, trustAll, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(HostnameVerifier { _, _ -> true })
    }

    companion object {
        const val FAILED_STATUS = -1
        private const val TIMEOUT_MS = 5_000
    }
}
