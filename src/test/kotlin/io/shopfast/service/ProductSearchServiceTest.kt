package io.shopfast.service

import io.shopfast.support.TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Busca da vitrine, contra um H2 de verdade com o esquema da aplicacao.
 *
 * Os casos de injecao rodam a consulta e conferem que o payload voltou vazio —
 * ou seja, foi tratado como texto e nao como sintaxe SQL.
 */
class ProductSearchServiceTest {

    private val database: EmbeddedDatabase = TestDatabase.start("busca-produtos")
    private val service = ProductSearchService(JdbcTemplate(database))

    @AfterEach
    fun shutdown() {
        database.shutdown()
    }

    @Test
    fun `busca por nome encontra o produto pelo trecho digitado`() {
        val encontrados = service.searchByName("Teclado")

        assertEquals(1, encontrados.size)
        assertEquals("Teclado Mecanico RGB", encontrados.first().name)
        assertEquals("eletronicos", encontrados.first().category)
        assertEquals(BigDecimal("389.00"), encontrados.first().price)
        assertEquals(15, encontrados.first().stockQuantity)
        assertTrue(encontrados.first().description!!.isNotBlank())
    }

    @Test
    fun `aspas do payload classico nao derrubam a clausula where`() {
        assertTrue(service.searchByName("' OR '1'='1").isEmpty())
        assertTrue(service.searchByName("'; DROP TABLE products; --").isEmpty())
        assertEquals(5, service.searchByName("").size)
    }

    @Test
    fun `busca por categoria ordena pela coluna pedida`() {
        val porNome = service.searchByCategory("eletronicos", "name")
        val porPreco = service.searchByCategory("eletronicos", "price")
        val porEstoque = service.searchByCategory("eletronicos", "stock")

        assertEquals(listOf("Fone Bluetooth ShopFast X1", "Teclado Mecanico RGB"), porNome.map { it.name })
        assertEquals(listOf(BigDecimal("249.90"), BigDecimal("389.00")), porPreco.map { it.price })
        assertEquals(listOf(15, 40), porEstoque.map { it.stockQuantity })
    }

    @Test
    fun `ordenacao fora da lista branca cai no padrao em vez de virar SQL`() {
        val injetado = service.searchByCategory("eletronicos", "name; DROP TABLE products")

        assertEquals(listOf("Fone Bluetooth ShopFast X1", "Teclado Mecanico RGB"), injetado.map { it.name })
        assertEquals(2, service.searchByCategory("eletronicos", "name").size)
    }

    @Test
    fun `faixa de preco filtra pelos limites informados`() {
        val encontrados = service.searchByPriceRange("200", "400")

        assertEquals(
            listOf("Fone Bluetooth ShopFast X1", "Teclado Mecanico RGB"),
            encontrados.map { it.name }.sorted(),
        )
    }

    @Test
    fun `faixa de preco que nao e numero e recusada antes da consulta`() {
        assertFailsWith<IllegalArgumentException> {
            service.searchByPriceRange("0", "1 OR 1=1")
        }
        assertFailsWith<IllegalArgumentException> {
            service.searchByPriceRange("(SELECT MIN(price) FROM products)", "999")
        }
    }
}
