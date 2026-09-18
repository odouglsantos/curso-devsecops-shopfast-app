package io.shopfast.web

import io.shopfast.support.WebTestSupport
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * As regras do `SecurityConfig`. Toda esta area era `permitAll()`, e e o que o
 * ZAP levantava como area administrativa aberta no Modulo 3.
 */
class SecurityRulesTest : WebTestSupport() {

    @Test
    fun `health e vitrine continuam publicos`() {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
        mockMvc.perform(get("/api/products/search").param("q", "Fone")).andExpect(status().isOk)
    }

    @Test
    fun `contrato OpenAPI fica publico para o ZAP importar`() {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk)
    }

    @Test
    fun `area administrativa exige sessao`() {
        mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/legacy/search").param("q", "Fone"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `cliente autenticado nao entra na area administrativa`() {
        mockMvc.perform(get("/admin/users").comSessao(joanaId))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `administrador entra na area administrativa`() {
        mockMvc.perform(get("/admin/users").comSessao(adminId, "ADMIN"))
            .andExpect(status().isOk)
    }

    @Test
    fun `actuator alem do health exige ADMIN`() {
        mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `pedido exige sessao`() {
        mockMvc.perform(get("/api/orders/mine")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `token adulterado nao vale como sessao`() {
        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer inventado"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `resposta sem sessao e 401 seco, sem redirect para pagina de login`() {
        mockMvc.perform(get("/api/orders/mine"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().doesNotExist("Location"))
            .andExpect(content().string(""))
    }

    @Test
    fun `cabecalhos de defesa acompanham a resposta`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(
                header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"),
            )
            .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
    }

    @Test
    fun `sessao e stateless, sem JSESSIONID`() {
        val resposta = mockMvc.perform(get("/actuator/health")).andReturn().response

        org.junit.jupiter.api.Assertions.assertNull(resposta.getCookie("JSESSIONID"))
    }

    @Test
    fun `POST sem token CSRF e recusado`() {
        mockMvc.perform(post("/api/auth/whoami").comSessao(joanaId))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `login e cadastro ficam de fora do CSRF porque ainda nao ha sessao`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("""{"username":"joana","password":"senha-errada"}"""),
        ).andExpect(status().isUnauthorized)
    }
}
