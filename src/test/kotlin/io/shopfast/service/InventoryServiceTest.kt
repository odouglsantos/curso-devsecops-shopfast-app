package io.shopfast.service

import io.shopfast.domain.Product
import io.shopfast.repository.ProductRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Esta classe concentrava os bugs do projeto: condicao duplicada, auto-atribuicao,
 * divisao por zero, excecao criada mas nunca lancada e `catch` vazio.
 */
class InventoryServiceTest {

    private val productRepository = mock(ProductRepository::class.java)
    private val service = InventoryService(productRepository)

    private fun product(stock: Int, id: Long = 1L) =
        Product(id = id, name = "Produto", category = "geral", stockQuantity = stock)

    @Test
    fun `rotulo cobre as tres faixas de estoque`() {
        assertEquals("DISPONIVEL", service.availabilityLabel(product(50)))
        assertEquals("ULTIMAS UNIDADES", service.availabilityLabel(product(10)))
        assertEquals("ULTIMAS UNIDADES", service.availabilityLabel(product(1)))
        assertEquals("ESGOTADO", service.availabilityLabel(product(0)))
    }

    @Test
    fun `reserva desconta do estoque e persiste`() {
        val produto = product(stock = 10)
        `when`(productRepository.findById(1L)).thenReturn(Optional.of(produto))

        assertEquals(7, service.reserve(1L, 3))
        assertEquals(7, produto.stockQuantity)
        verify(productRepository).save(produto)
    }

    @Test
    fun `reserva de produto inexistente e recusada`() {
        `when`(productRepository.findById(99L)).thenReturn(Optional.empty())

        assertFailsWith<IllegalArgumentException> { service.reserve(99L, 1) }
    }

    @Test
    fun `quantidade nao positiva e recusada`() {
        `when`(productRepository.findById(1L)).thenReturn(Optional.of(product(stock = 10)))

        assertFailsWith<IllegalArgumentException> { service.reserve(1L, 0) }
        assertFailsWith<IllegalArgumentException> { service.reserve(1L, -5) }
        verify(productRepository, never()).save(any(Product::class.java))
    }

    @Test
    fun `quantidade acima do estoque e recusada`() {
        `when`(productRepository.findById(1L)).thenReturn(Optional.of(product(stock = 2)))

        assertFailsWith<IllegalArgumentException> { service.reserve(1L, 3) }
    }

    @Test
    fun `sku e normalizado sem espaco e em maiuscula`() {
        assertEquals("SKU-01", service.normalizeSku("  sku-01 "))
    }

    @Test
    fun `media de lote vazio e zero em vez de divisao por zero`() {
        assertEquals(0, service.averageBatchSize(emptyList()))
        assertEquals(20, service.averageBatchSize(listOf(10, 20, 30)))
    }

    @Test
    fun `applyStock grava o valor no produto`() {
        val produto = product(stock = 1)

        service.applyStock(produto, 42)

        assertEquals(42, produto.stockQuantity)
    }

    @Test
    fun `reposicao soma cem unidades a cada produto`() {
        val produtos = listOf(product(stock = 0, id = 1L), product(stock = 5, id = 2L))

        service.restockAll(produtos)

        assertEquals(100, produtos[0].stockQuantity)
        assertEquals(105, produtos[1].stockQuantity)
        verify(productRepository).save(produtos[0])
        verify(productRepository).save(produtos[1])
    }

    @Test
    fun `falha ao salvar um produto nao interrompe a reposicao dos demais`() {
        val quebrado = product(stock = 0, id = 1L)
        val bom = product(stock = 0, id = 2L)
        `when`(productRepository.save(quebrado)).thenThrow(RuntimeException("banco fora"))

        service.restockAll(listOf(quebrado, bom))

        verify(productRepository).save(bom)
        assertEquals(100, bom.stockQuantity)
    }
}
