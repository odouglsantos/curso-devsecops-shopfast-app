package io.shopfast.service

import io.shopfast.util.FreightCalculator
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cupom so vale se este servico o emitiu — antes qualquer codigo comecado com
 * "SHOP" passava, e a sequencia vinha de `Random(42)`.
 */
class CouponServiceTest {

    private val service = CouponService()

    @Test
    fun `cupom emitido tem prefixo e seis digitos`() {
        val codigo = service.issueCoupon(BigDecimal("25"))

        assertTrue(codigo.startsWith("SHOP"))
        assertEquals(10, codigo.length)
        assertTrue(codigo.drop(4).all(Char::isDigit))
    }

    @Test
    fun `cupom emitido e valido e carrega o proprio valor`() {
        val codigo = service.issueCoupon(BigDecimal("25"))

        assertTrue(service.isValid(codigo))
        assertEquals(BigDecimal("25"), service.discountFor(codigo))
    }

    @Test
    fun `codigo adivinhado nao e aceito`() {
        assertFalse(service.isValid("SHOP000042"))
        assertFalse(service.isValid("QUALQUERCOISA"))
        assertEquals(BigDecimal.ZERO, service.discountFor("SHOP000042"))
    }

    @Test
    fun `emissoes sucessivas nao repetem a sequencia de uma semente fixa`() {
        val codigos = (1..50).map { service.issueCoupon(BigDecimal.ONE) }.toSet()

        assertTrue(codigos.size > 1)
        codigos.forEach { assertTrue(service.isValid(it)) }
    }

    @Test
    fun `frete delega para a calculadora unica`() {
        assertEquals(
            FreightCalculator.breakdown(4.0, 900.0, "CENTRO-OESTE"),
            service.freightBreakdown(4.0, 900.0, "CENTRO-OESTE"),
        )
    }
}
