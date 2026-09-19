package io.shopfast.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * As entidades sao carregadas e gravadas pelo JPA, entao cada coluna precisa de
 * acessor funcionando. O teste percorre os campos de ida e volta.
 */
class EntitiesTest {

    @Test
    fun `produto guarda e devolve cada coluna`() {
        val produto = Product()

        assertNull(produto.id)
        assertEquals("", produto.name)
        assertEquals(BigDecimal.ZERO, produto.price)
        assertEquals(0, produto.stockQuantity)
        assertNull(produto.description)

        produto.id = 1L
        produto.name = "Fone"
        produto.category = "eletronicos"
        produto.price = BigDecimal("249.90")
        produto.stockQuantity = 40
        produto.description = "Over-ear"

        assertEquals(1L, produto.id)
        assertEquals("Fone", produto.name)
        assertEquals("eletronicos", produto.category)
        assertEquals(BigDecimal("249.90"), produto.price)
        assertEquals(40, produto.stockQuantity)
        assertEquals("Over-ear", produto.description)
    }

    @Test
    fun `usuario nasce como CUSTOMER e sem cartao`() {
        val user = User()

        assertNull(user.id)
        assertEquals("", user.username)
        assertEquals("", user.passwordHash)
        assertEquals("", user.email)
        assertEquals("CUSTOMER", user.role)
        assertNull(user.creditCardNumber)

        user.id = 2L
        user.username = "joana"
        user.passwordHash = "\$2a\$12\$hash"
        user.email = "joana@example.com"
        user.role = "ADMIN"
        user.creditCardNumber = "4111111111111111"

        assertEquals(2L, user.id)
        assertEquals("joana", user.username)
        assertEquals("\$2a\$12\$hash", user.passwordHash)
        assertEquals("joana@example.com", user.email)
        assertEquals("ADMIN", user.role)
        assertEquals("4111111111111111", user.creditCardNumber)
    }

    @Test
    fun `pedido nasce pendente com total zerado`() {
        val agora = Instant.now()
        val order = Order()

        assertNull(order.id)
        assertEquals(0L, order.userId)
        assertEquals("PENDING", order.status)
        assertEquals(BigDecimal.ZERO, order.total)
        assertNull(order.couponCode)

        order.id = 7L
        order.userId = 2L
        order.status = "PAID"
        order.total = BigDecimal("449.80")
        order.couponCode = "SHOP000042"
        order.createdAt = agora

        assertEquals(7L, order.id)
        assertEquals(2L, order.userId)
        assertEquals("PAID", order.status)
        assertEquals(BigDecimal("449.80"), order.total)
        assertEquals("SHOP000042", order.couponCode)
        assertEquals(agora, order.createdAt)
    }
}
