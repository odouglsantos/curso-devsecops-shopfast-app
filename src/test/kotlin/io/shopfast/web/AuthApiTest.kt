package io.shopfast.web

import io.shopfast.support.WebTestSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Entrada de sessao. O cookie voltou a ter `HttpOnly`, `Secure` e
 * `SameSite=Strict`, o cadastro parou de devolver o hash da senha e o reset
 * deixou de expor o token na resposta.
 */
class AuthApiTest : WebTestSupport() {

    private fun json(corpo: String) = post("/api/auth/login")
        .contentType("application/json")
        .content(corpo)

    @Test
    fun `login valido devolve token e cookie protegido`() {
        val resposta = mockMvc.perform(json("""{"username":"joana","password":"Joana@2026"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn()
            .response

        val setCookie = assertNotNull(resposta.getHeader(HttpHeaders.SET_COOKIE))
        assertContains(setCookie, "SHOPFAST_SESSION=")
        assertContains(setCookie, "HttpOnly")
        assertContains(setCookie, "Secure")
        assertContains(setCookie, "SameSite=Strict")
        assertContains(setCookie, "Max-Age=3600")
    }

    @Test
    fun `senha errada e usuario inexistente respondem a mesma coisa`() {
        val comSenhaErrada = mockMvc.perform(json("""{"username":"joana","password":"errada"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("credenciais invalidas"))
            .andReturn().response.contentAsString

        val semConta = mockMvc.perform(json("""{"username":"ninguem","password":"errada"}"""))
            .andExpect(status().isUnauthorized)
            .andReturn().response.contentAsString

        kotlin.test.assertEquals(comSenhaErrada, semConta)
    }

    @Test
    fun `cadastro nao devolve o hash da senha`() {
        val corpo = mockMvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content("""{"username":"novo-cliente","password":"Nova@2026","email":"novo@example.com"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("novo-cliente"))
            .andExpect(jsonPath("$.email").value("novo@example.com"))
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andReturn().response.contentAsString

        assertFalse(corpo.contains("passwordHash"))
        assertFalse(corpo.contains("Nova@2026"))
        assertFalse(corpo.contains("\$2a\$"))
    }

    @Test
    fun `pedido de reset responde sempre igual e nao devolve o token`() {
        val existente = mockMvc.perform(
            post("/api/auth/password-reset")
                .contentType("application/json")
                .content("""{"username":"joana"}"""),
        )
            .andExpect(status().isAccepted)
            .andReturn().response.contentAsString

        val inexistente = mockMvc.perform(
            post("/api/auth/password-reset")
                .contentType("application/json")
                .content("""{"username":"ninguem"}"""),
        )
            .andExpect(status().isAccepted)
            .andReturn().response.contentAsString

        kotlin.test.assertEquals(existente, inexistente)
        assertContains(existente, "Se a conta existir")
        assertFalse(existente.contains("token"))
    }

    @Test
    fun `whoami devolve o papel do contexto, nao o que o cliente pediu`() {
        mockMvc.perform(post("/api/auth/whoami").comSessao(joanaId).comCsrf())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(joanaId.toString()))
            .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))

        mockMvc.perform(post("/api/auth/whoami").comSessao(adminId, "ADMIN").comCsrf())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
    }

    @Test
    fun `token emitido no login abre as rotas autenticadas`() {
        val token = mockMvc.perform(json("""{"username":"joana","password":"Joana@2026"}"""))
            .andReturn().response.contentAsString
            .substringAfter("\"token\":\"").substringBefore("\"")

        assertTrue(token.isNotBlank())
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/orders/mine")
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isOk)
    }
}
