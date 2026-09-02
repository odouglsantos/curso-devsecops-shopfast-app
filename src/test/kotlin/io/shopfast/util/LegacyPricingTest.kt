package io.shopfast.util

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * Unico teste do projeto. Cobre uma funcao de rotulo e mais nada — e o que
 * mantem a cobertura perto de 1,6 % e reprova a condicao de coverage do
 * Quality Gate na Aula 2.5.
 */
class LegacyPricingTest {

    @Test
    fun `cliente premium tem frete gratis`() {
        assertEquals("Cliente PREMIUM com frete gratis", LegacyPricing.labelFor("PREMIUM"))
    }

    @Test
    fun `cliente comum nao tem frete gratis`() {
        assertEquals("Cliente padrao", LegacyPricing.labelFor("BRONZE"))
    }

    @Test
    fun `preco arredonda para o real mais proximo`() {
        assertEquals(BigDecimal("13"), LegacyPricing.roundPrice(BigDecimal("12.90")))
        assertEquals(BigDecimal("12"), LegacyPricing.roundPrice(BigDecimal("12.10")))
    }
}
