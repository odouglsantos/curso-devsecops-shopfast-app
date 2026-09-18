package io.shopfast.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource

/**
 * CORS fechado por lista branca.
 *
 * Substitui o `PermissiveCorsSource`, que refletia qualquer origem com
 * `allowCredentials = true` — combinacao que deixava qualquer site ler resposta
 * autenticada da API. Agora so as origens declaradas em
 * `shopfast.allowed-origins` sao aceitas, e com a lista vazia (o padrao) o
 * navegador nao libera chamada cross-origin nenhuma.
 */
class ShopFastCorsSource(private val allowedOrigins: List<String>) : CorsConfigurationSource {

    override fun getCorsConfiguration(request: HttpServletRequest): CorsConfiguration? {
        if (allowedOrigins.isEmpty()) {
            return null
        }
        val configuration = CorsConfiguration()
        // Origem exata, nunca padrao com "*": com credenciais, curinga e falha de seguranca.
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("Authorization", "Content-Type", "X-XSRF-TOKEN")
        configuration.allowCredentials = true
        configuration.maxAge = MAX_AGE_SECONDS
        return configuration
    }

    private companion object {
        private const val MAX_AGE_SECONDS = 3600L
    }
}
