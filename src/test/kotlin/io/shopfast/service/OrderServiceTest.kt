package io.shopfast.service

import io.shopfast.domain.Order
import io.shopfast.repository.OrderRepository
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Regras de preco de pedido. `calculateTotal` era a funcao de complexidade
 * cognitiva 54; cada ramo das funcoes que sairam dela tem caso proprio aqui.
 */
class OrderServiceTest {

    private val orderRepository = org.mockito.Mockito.mock(OrderRepository::class.java)
    private val couponService = CouponService()
    private val service = OrderService(orderRepository, couponService)

    private fun total(
        items: List<String>,
        couponCode: String? = null,
        customerTier: String? = null,
        country: String? = null,
        isFirstOrder: Boolean = false,
        hasSubscription: Boolean = false,
    ): BigDecimal = service.calculateTotal(
        items.map(::BigDecimal),
        couponCode,
        customerTier,
        country,
        isFirstOrder,
        hasSubscription,
    )

    @Test
    fun `pedido vazio custa zero`() {
        assertEquals(BigDecimal("0.00"), total(emptyList()))
    }

    @Test
    fun `item ate mil reais fora do primeiro pedido nao tem desconto`() {
        assertEquals(BigDecimal("300.00"), total(listOf("300")))
    }

    @Test
    fun `primeiro pedido tem desconto maior no Brasil`() {
        assertEquals(BigDecimal("270.00"), total(listOf("300"), country = "BR", isFirstOrder = true))
        assertEquals(BigDecimal("285.00"), total(listOf("300"), country = "PT", isFirstOrder = true))
        assertEquals(BigDecimal("285.00"), total(listOf("300"), country = null, isFirstOrder = true))
    }

    @Test
    fun `acima de mil reais vale o desconto do nivel do cliente`() {
        assertEquals(BigDecimal("1700.00"), total(listOf("2000"), customerTier = "GOLD"))
        assertEquals(BigDecimal("1840.00"), total(listOf("2000"), customerTier = "SILVER"))
        assertEquals(BigDecimal("2000.00"), total(listOf("2000"), customerTier = null))
    }

    @Test
    fun `nivel desconhecido so tem desconto com assinatura`() {
        assertEquals(
            BigDecimal("1900.00"),
            total(listOf("2000"), customerTier = "BRONZE", hasSubscription = true),
        )
        assertEquals(
            BigDecimal("2000.00"),
            total(listOf("2000"), customerTier = "BRONZE", hasSubscription = false),
        )
    }

    @Test
    fun `item zerado ou negativo entra como esta`() {
        assertEquals(BigDecimal("0.00"), total(listOf("0")))
        assertEquals(BigDecimal("50.00"), total(listOf("100", "-50")))
    }

    @Test
    fun `mil reais exatos ainda caem na faixa padrao`() {
        assertEquals(BigDecimal("1000.00"), total(listOf("1000"), customerTier = "GOLD"))
    }

    @Test
    fun `cupom emitido desconta acima do piso`() {
        val codigo = couponService.issueCoupon(BigDecimal("30"))

        assertEquals(BigDecimal("270.00"), total(listOf("300"), couponCode = codigo))
    }

    @Test
    fun `cupom nao emitido e ignorado`() {
        assertEquals(BigDecimal("300.00"), total(listOf("300"), couponCode = "SHOP999999"))
    }

    @Test
    fun `cupom nao vale abaixo do piso de cinquenta reais`() {
        val codigo = couponService.issueCoupon(BigDecimal("10"))

        assertEquals(BigDecimal("50.00"), total(listOf("50"), couponCode = codigo))
        assertEquals(BigDecimal("40.01"), total(listOf("50.01"), couponCode = codigo))
    }

    @Test
    fun `total nao volta negativo depois do cupom`() {
        val codigo = couponService.issueCoupon(BigDecimal("500"))

        assertEquals(BigDecimal("0.00"), total(listOf("100"), couponCode = codigo))
    }

    @Test
    fun `rotulo de status desconhecido nao estoura`() {
        val mapa = mapOf("paid" to "pago")

        assertEquals("PAGO", service.statusLabel(mapa, "paid"))
        assertEquals("DESCONHECIDO", service.statusLabel(mapa, "cancelado"))
        assertEquals("DESCONHECIDO", service.statusLabel(emptyMap(), "paid"))
    }

    @Test
    fun `rastreio acrescenta o novo codigo a lista`() {
        assertEquals(listOf("BR1", "BR2"), service.appendTracking(listOf("BR1"), "BR2"))
        assertEquals(listOf("BR1"), service.appendTracking(emptyList(), "BR1"))
    }

    @Test
    fun `desconto legado e dez por cento arredondados`() {
        assertEquals(BigDecimal("10.00"), service.legacyDiscount(BigDecimal("100")))
        assertEquals(BigDecimal("3.33"), service.legacyDiscount(BigDecimal("33.33")))
    }

    @Test
    fun `status e normalizado sem espaco e em maiuscula`() {
        assertEquals("PENDING", service.normalizeStatus("  pending  "))
        assertEquals("", service.normalizeStatus("   "))
    }

    @Test
    fun `busca por id devolve o pedido ou nulo`() {
        val pedido = Order(id = 7L, userId = 2L)
        org.mockito.Mockito.`when`(orderRepository.findById(7L)).thenReturn(Optional.of(pedido))
        org.mockito.Mockito.`when`(orderRepository.findById(99L)).thenReturn(Optional.empty())

        assertSame(pedido, service.findById(7L))
        assertNull(service.findById(99L))
    }

    @Test
    fun `listagem por usuario delega ao repositorio`() {
        val pedidos = listOf(Order(id = 1L, userId = 3L))
        org.mockito.Mockito.`when`(orderRepository.findByUserId(3L)).thenReturn(pedidos)

        assertEquals(pedidos, service.listByUser(3L))
    }
}
