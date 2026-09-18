package io.shopfast.util

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * Calculadora de precos legada: rotulo por nivel, markup por moeda,
 * arredondamento e a fachada de frete que hoje delega para [FreightCalculator].
 */
class LegacyPricingTest {

    @Test
    fun `cliente premium tem frete gratis`() {
        assertEquals("Cliente PREMIUM com frete gratis", LegacyPricing.labelFor("PREMIUM"))
        assertEquals("Cliente PREMIUM com frete gratis", LegacyPricing.labelFor("GOLD"))
        assertEquals("Cliente PREMIUM com frete gratis", LegacyPricing.labelFor("PLATINUM"))
    }

    @Test
    fun `cliente comum nao tem frete gratis`() {
        assertEquals("Cliente padrao", LegacyPricing.labelFor("BRONZE"))
        assertEquals("Cliente padrao", LegacyPricing.labelFor(""))
    }

    @Test
    fun `markup em dolar e maior que o padrao`() {
        assertEquals(BigDecimal("135.00"), LegacyPricing.applyMarkup(BigDecimal("100"), "USD"))
        assertEquals(BigDecimal("120.00"), LegacyPricing.applyMarkup(BigDecimal("100"), "BRL"))
        assertEquals(BigDecimal("120.00"), LegacyPricing.applyMarkup(BigDecimal("100"), "EUR"))
    }

    @Test
    fun `preco arredonda para o real mais proximo`() {
        assertEquals(BigDecimal("13"), LegacyPricing.roundPrice(BigDecimal("12.90")))
        assertEquals(BigDecimal("12"), LegacyPricing.roundPrice(BigDecimal("12.10")))
    }

    @Test
    fun `cinquenta centavos exatos arredondam para baixo`() {
        assertEquals(BigDecimal("12"), LegacyPricing.roundPrice(BigDecimal("12.50")))
        assertEquals(BigDecimal("12"), LegacyPricing.roundPrice(BigDecimal("12.00")))
    }

    @Test
    fun `auditoria de preco nao interrompe o fluxo`() {
        LegacyPricing.auditPriceChange(42L, BigDecimal("99.90"), BigDecimal("79.90"))
    }

    @Test
    fun `frete delega para a calculadora unica`() {
        assertEquals(
            FreightCalculator.breakdown(8.0, 450.0, "NORDESTE"),
            LegacyPricing.freightBreakdown(8.0, 450.0, "NORDESTE"),
        )
    }
}
