package io.shopfast.config

import io.shopfast.service.AuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Traduz o token de sessao do ShopFast em autenticacao do Spring Security.
 *
 * Antes nao existia autenticacao nenhuma: a cadeia de filtros era `permitAll`
 * de ponta a ponta, e por isso `/admin` ficava aberto e o IDOR de
 * `/api/orders/{id}` nao tinha como ser barrado. Com este filtro, o papel e o
 * dono da sessao passam a existir no `SecurityContext`, e as regras de
 * autorizacao do [SecurityConfig] e dos controllers tem em que se apoiar.
 *
 * O token e lido do cabecalho `Authorization: Bearer <token>` ou do cookie de
 * sessao, e so vale se a assinatura HMAC conferir.
 */
@Component
class SessionTokenAuthenticationFilter(private val authService: AuthService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            tokenFrom(request)
                ?.let { token -> authService.resolveSession(token) }
                ?.let { principal ->
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_${principal.role}"))
                    val authentication = UsernamePasswordAuthenticationToken(
                        principal.userId,
                        null,
                        authorities,
                    )
                    SecurityContextHolder.getContext().authentication = authentication
                }
        }
        filterChain.doFilter(request, response)
    }

    private fun tokenFrom(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            return header.substring(BEARER_PREFIX.length).trim().ifBlank { null }
        }
        return request.cookies
            ?.firstOrNull { cookie -> cookie.name == SESSION_COOKIE }
            ?.value
            ?.ifBlank { null }
    }

    companion object {
        const val SESSION_COOKIE = "SHOPFAST_SESSION"
        private const val BEARER_PREFIX = "Bearer "
    }
}
