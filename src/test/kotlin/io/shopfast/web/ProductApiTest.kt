package io.shopfast.web

import io.shopfast.support.WebTestSupport
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Vitrine publica. O `/debug-search`, que devolvia o stacktrace inteiro, saiu; a
 * busca continua aberta, agora sobre consultas parametrizadas.
 */
class ProductApiTest : WebTestSupport() {

    @Test
    fun `busca por nome devolve o produto`() {
        mockMvc.perform(get("/api/products/search").param("q", "Teclado"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Teclado Mecanico RGB"))
    }

    @Test
    fun `payload de injecao volta vazio em vez do catalogo`() {
        mockMvc.perform(get("/api/products/search").param("q", "' OR '1'='1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `busca por categoria aceita a ordenacao da lista branca`() {
        mockMvc.perform(
            get("/api/products/by-category").param("category", "eletronicos").param("sort", "price"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Fone Bluetooth ShopFast X1"))

        mockMvc.perform(get("/api/products/by-category").param("category", "eletronicos"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `faixa de preco filtra pelos limites`() {
        mockMvc.perform(get("/api/products/by-price").param("min", "500").param("max", "700"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Cafeteira Expresso Compacta"))
    }

    @Test
    fun `faixa de preco invalida vira 400 sem stacktrace`() {
        mockMvc.perform(get("/api/products/by-price").param("min", "0").param("max", "1 OR 1=1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("requisicao invalida"))
            .andExpect(jsonPath("$.trace").doesNotExist())
    }

    @Test
    fun `endpoint de depuracao nao existe mais`() {
        mockMvc.perform(get("/api/products/debug-search").param("q", "x"))
            .andExpect(status().isNotFound)
    }
}
