package io.shopfast.support

import io.shopfast.service.AuthService
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Base dos testes que sobem a aplicacao inteira.
 *
 * Passar pela cadeia de filtros de verdade e o que da valor a estes testes: as
 * regras do `SecurityConfig` (papel por rota, CSRF, cabecalhos) sao exercitadas
 * de fato, e nao simuladas. Por isso o `MockMvc` e montado sobre o contexto
 * real, com o `springSecurityFilterChain` no caminho.
 */
@SpringBootTest
@TestPropertySource(properties = ["shopfast.report-directory=\${java.io.tmpdir}/shopfast-testes"])
abstract class WebTestSupport {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var springSecurityFilterChain: FilterChainProxy

    @Autowired
    protected lateinit var authService: AuthService

    protected lateinit var mockMvc: MockMvc

    protected lateinit var csrfCookie: Cookie

    /** Identificadores vindos do `data.sql`: admin, joana e carlos. */
    protected val adminId = 1L
    protected val joanaId = 2L
    protected val carlosId = 3L

    @BeforeEach
    fun prepararCliente() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(
                springSecurityFilterChain,
            )
            .build()

        // O repositorio de CSRF emite o cookie XSRF-TOKEN em qualquer resposta,
        // e o cliente o devolve no cabecalho X-XSRF-TOKEN (double submit).
        val resposta = mockMvc.perform(get("/actuator/health")).andReturn().response
        csrfCookie = checkNotNull(resposta.getCookie("XSRF-TOKEN")) {
            "o cookie de CSRF deveria ser emitido em toda resposta"
        }
    }

    protected fun tokenDe(userId: Long, role: String): String =
        authService.issueSessionToken(userId, role)

    protected fun MockHttpServletRequestBuilder.comSessao(
        userId: Long,
        role: String = "CUSTOMER",
    ): MockHttpServletRequestBuilder = header("Authorization", "Bearer ${tokenDe(userId, role)}")

    protected fun MockHttpServletRequestBuilder.comCsrf(): MockHttpServletRequestBuilder =
        cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.value)
}
