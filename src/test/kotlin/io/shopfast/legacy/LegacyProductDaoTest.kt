package io.shopfast.legacy

import io.shopfast.support.TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DAO legado em Java: era o achado central de `java:S2077`. Os testes rodam
 * contra um H2 real, entao o payload de injecao e de fato executado — e volta
 * vazio, porque chega como parametro e nunca como sintaxe.
 */
class LegacyProductDaoTest {

    private val database: EmbeddedDatabase = TestDatabase.start("legacy-produtos")
    private val dao = LegacyProductDao(database)

    @AfterEach
    fun shutdown() {
        database.shutdown()
    }

    @Test
    fun `busca por nome devolve o produto correspondente`() {
        assertEquals(listOf("Teclado Mecanico RGB"), dao.searchByName("Teclado"))
        assertEquals(5, dao.searchByName("").size)
    }

    @Test
    fun `payload de injecao nao devolve o catalogo inteiro`() {
        assertTrue(dao.searchByName("' OR '1'='1").isEmpty())
        assertTrue(dao.searchByName("x' UNION SELECT password_hash FROM users --").isEmpty())
        assertEquals(5, dao.searchByName("").size)
    }

    @Test
    fun `ordenacao vem da lista branca`() {
        assertEquals(
            listOf("Fone Bluetooth ShopFast X1", "Teclado Mecanico RGB"),
            dao.searchByCategory("eletronicos", "name"),
        )
        assertEquals(
            listOf("Fone Bluetooth ShopFast X1", "Teclado Mecanico RGB"),
            dao.searchByCategory("eletronicos", "price"),
        )
        assertEquals(
            listOf("Teclado Mecanico RGB", "Fone Bluetooth ShopFast X1"),
            dao.searchByCategory("eletronicos", "stock"),
        )
    }

    @Test
    fun `ordenacao desconhecida cai no padrao sem virar SQL`() {
        assertEquals(
            listOf("Fone Bluetooth ShopFast X1", "Teclado Mecanico RGB"),
            dao.searchByCategory("eletronicos", "name; DROP TABLE products --"),
        )
        assertEquals(5, dao.searchByName("").size)
    }

    @Test
    fun `remocao por nome afeta apenas a linha informada`() {
        assertEquals(1, dao.deleteByName("Teclado Mecanico RGB"))
        assertEquals(0, dao.deleteByName("Produto Que Nao Existe"))
        assertEquals(4, dao.searchByName("").size)
    }

    @Test
    fun `remocao com payload de injecao nao apaga a tabela`() {
        assertEquals(0, dao.deleteByName("x' OR '1'='1"))
        assertEquals(5, dao.searchByName("").size)
    }

    @Test
    fun `falha de banco vira log, nao stacktrace no stdout`() {
        val dataSource = mock(DataSource::class.java)
        `when`(dataSource.connection).thenThrow(SQLException("banco fora do ar"))
        val indisponivel = LegacyProductDao(dataSource)

        assertTrue(indisponivel.searchByName("Teclado").isEmpty())
        assertTrue(indisponivel.searchByCategory("eletronicos", "name").isEmpty())
        assertEquals(0, indisponivel.deleteByName("Teclado Mecanico RGB"))
    }
}
