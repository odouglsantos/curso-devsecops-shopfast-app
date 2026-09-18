package io.shopfast.config

import io.shopfast.service.AuthService
import io.shopfast.util.CryptoUtils
import io.shopfast.repository.UserRepository
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * O filtro que traduz o token assinado em `Authentication`. Sem ele nao havia
 * identidade no `SecurityContext`, e por isso `/admin` e o IDOR nao tinham como
 * ser barrados.
 */
class SessionTokenAuthenticationFilterTest {

    private val properties = AppProperties(jwtSecret = "segredo-de-assinatura", encryptionKey = "chave")
    private val authService = AuthService(
        mock(UserRepository::class.java),
        properties,
        BCryptPasswordEncoder(4),
        CryptoUtils(properties),
    )
    private val filter = SessionTokenAuthenticationFilter(authService)

    @AfterEach
    fun limparContexto() {
        SecurityContextHolder.clearContext()
    }

    private fun executar(configurar: MockHttpServletRequest.() -> Unit = {}): MockFilterChain {
        val request = MockHttpServletRequest("GET", "/api/orders/mine").apply(configurar)
        val chain = MockFilterChain()
        filter.doFilter(request, MockHttpServletResponse(), chain)
        return chain
    }

    @Test
    fun `requisicao sem token segue sem autenticacao`() {
        val chain = executar()

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
    }

    @Test
    fun `token valido no cabecalho Authorization vira autenticacao`() {
        val token = authService.issueSessionToken(2L, "CUSTOMER")

        executar { addHeader("Authorization", "Bearer $token") }

        val authentication = assertNotNull(SecurityContextHolder.getContext().authentication)
        assertEquals(2L, authentication.principal)
        assertEquals(listOf(SimpleGrantedAuthority("ROLE_CUSTOMER")), authentication.authorities.toList())
    }

    @Test
    fun `prefixo Bearer e aceito sem diferenciar maiuscula`() {
        val token = authService.issueSessionToken(9L, "ADMIN")

        executar { addHeader("Authorization", "bearer $token") }

        val authentication = assertNotNull(SecurityContextHolder.getContext().authentication)
        assertEquals(9L, authentication.principal)
        assertEquals(listOf(SimpleGrantedAuthority("ROLE_ADMIN")), authentication.authorities.toList())
    }

    @Test
    fun `token valido no cookie de sessao vira autenticacao`() {
        val token = authService.issueSessionToken(3L, "CUSTOMER")

        executar {
            setCookies(Cookie("outro", "x"), Cookie(SessionTokenAuthenticationFilter.SESSION_COOKIE, token))
        }

        assertEquals(3L, assertNotNull(SecurityContextHolder.getContext().authentication).principal)
    }

    @Test
    fun `cabecalho Bearer vazio nao autentica`() {
        executar { addHeader("Authorization", "Bearer    ") }

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `cabecalho de outro esquema nao autentica`() {
        executar { addHeader("Authorization", "Basic YWRtaW46YWRtaW4=") }

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `cookie de sessao vazio nao autentica`() {
        executar { setCookies(Cookie(SessionTokenAuthenticationFilter.SESSION_COOKIE, "")) }

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `cookie de outro nome nao autentica`() {
        executar { setCookies(Cookie("XSRF-TOKEN", "abc")) }

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `token adulterado nao autentica`() {
        executar { addHeader("Authorization", "Bearer token-invalido") }

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `autenticacao ja presente no contexto e preservada`() {
        val existente = UsernamePasswordAuthenticationToken(42L, null, emptyList())
        SecurityContextHolder.getContext().authentication = existente

        executar { addHeader("Authorization", "Bearer ${authService.issueSessionToken(2L, "ADMIN")}") }

        assertEquals(existente, SecurityContextHolder.getContext().authentication)
    }
}
