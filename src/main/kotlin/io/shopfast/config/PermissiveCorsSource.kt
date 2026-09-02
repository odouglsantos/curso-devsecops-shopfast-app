package io.shopfast.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource

/**
 * VULN (kotlin:S5122): CORS liberado para qualquer origem, com credenciais habilitadas.
 * Qualquer site consegue ler respostas autenticadas da API do ShopFast.
 */
class PermissiveCorsSource : CorsConfigurationSource {

    override fun getCorsConfiguration(request: HttpServletRequest): CorsConfiguration {
        val configuration = CorsConfiguration()
        configuration.addAllowedOriginPattern("*")
        configuration.addAllowedMethod("*")
        configuration.addAllowedHeader("*")
        configuration.allowCredentials = true
        configuration.maxAge = 86400
        return configuration
    }
}
