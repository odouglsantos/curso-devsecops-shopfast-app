package io.shopfast.web

import io.shopfast.service.AuthService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `whoami` le o papel do `SecurityContext`. Sessao sem autoridade nenhuma
 * devolve papel nulo, em vez de estourar.
 */
class AuthControllerTest {

    private val controller = AuthController(mock(AuthService::class.java))

    @Test
    fun `sessao sem autoridade devolve papel nulo`() {
        val semPapel = UsernamePasswordAuthenticationToken("7", null, emptyList())

        val resposta = controller.whoami(semPapel)

        assertEquals("7", resposta["userId"])
        assertNull(resposta["role"])
    }
}
