package io.shopfast.web

import io.shopfast.service.OrderService
import io.shopfast.service.PaymentService
import io.shopfast.support.WebTestSupport
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFailsWith

/**
 * O IDOR de `/api/orders/{id}`: bastava trocar o numero no caminho para ler o
 * pedido de outra pessoa, e trocar o `userId` do corpo para cobrar no cartao
 * alheio. Pedido de terceiro agora responde 404, nao 403 — 403 ja confirmaria
 * que o id existe.
 */
class OrderApiTest : WebTestSupport() {

    @Test
    fun `dono le o proprio pedido`() {
        mockMvc.perform(get("/api/orders/1").comSessao(joanaId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.userId").value(joanaId))
    }

    @Test
    fun `pedido de outro usuario responde 404, sem confirmar que existe`() {
        mockMvc.perform(get("/api/orders/1").comSessao(carlosId))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/api/orders/999").comSessao(carlosId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `administrador enxerga qualquer pedido`() {
        mockMvc.perform(get("/api/orders/1").comSessao(adminId, "ADMIN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(joanaId))
    }

    @Test
    fun `listagem propria traz apenas os pedidos da sessao`() {
        mockMvc.perform(get("/api/orders/mine").comSessao(joanaId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].userId").value(joanaId))

        mockMvc.perform(get("/api/orders/mine").comSessao(99L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `checkout cobra pela sessao e devolve total e recibo`() {
        mockMvc.perform(
            post("/api/orders/checkout")
                .comSessao(joanaId)
                .comCsrf()
                .contentType("application/json")
                .content("""{"items":[2000],"couponCode":null,"cardNumber":"4111111111111111","cvv":"123"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1700.00))
            .andExpect(jsonPath("$.receipt").isNotEmpty)
    }

    @Test
    fun `checkout sem token CSRF e recusado`() {
        mockMvc.perform(
            post("/api/orders/checkout")
                .comSessao(joanaId)
                .contentType("application/json")
                .content("""{"items":[100],"couponCode":null,"cardNumber":"4111111111111111","cvv":"123"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `sessao sem identificacao numerica e recusada`() {
        val controller = OrderController(
            mock(OrderService::class.java),
            mock(PaymentService::class.java),
        )
        val semUsuario = UsernamePasswordAuthenticationToken("anonimo", null, emptyList())

        assertFailsWith<IllegalStateException> { controller.listMine(semUsuario) }
    }
}
