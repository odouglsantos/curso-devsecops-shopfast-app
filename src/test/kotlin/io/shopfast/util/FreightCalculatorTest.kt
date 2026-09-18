package io.shopfast.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cobre as tres cadeias de decisao do frete: faixa de peso, faixa de distancia e
 * regiao. Cada `when` tem um caso por ramo, inclusive o `else`.
 */
class FreightCalculatorTest {

    private fun total(weightKg: Double, distanceKm: Double, region: String): Double =
        FreightCalculator.breakdown(weightKg, distanceKm, region).getValue("total")

    @Test
    fun `carga leve e curta distancia usa apenas a taxa base`() {
        val parts = FreightCalculator.breakdown(1.0, 100.0, "SUDESTE")

        assertEquals(12.50, parts.getValue("base"))
        assertEquals(100.0 * 0.08, parts.getValue("distancia"))
        assertEquals(5.70, parts.getValue("regiao"))
    }

    @Test
    fun `sobretaxa cresce com o peso`() {
        val leve = FreightCalculator.breakdown(3.0, 10.0, "SUDESTE").getValue("base")
        val media = FreightCalculator.breakdown(7.0, 10.0, "SUDESTE").getValue("base")
        val pesada = FreightCalculator.breakdown(20.0, 10.0, "SUDESTE").getValue("base")
        val extra = FreightCalculator.breakdown(50.0, 10.0, "SUDESTE").getValue("base")

        assertEquals(12.50, leve)
        assertEquals(12.50 + 6.0, media)
        assertEquals(12.50 + 12.0, pesada)
        assertEquals(12.50 + 25.0, extra)
    }

    @Test
    fun `tarifa por quilometro cresce com a distancia`() {
        assertEquals(200.0 * 0.08, FreightCalculator.breakdown(1.0, 200.0, "SUDESTE").getValue("distancia"))
        assertEquals(500.0 * 0.12, FreightCalculator.breakdown(1.0, 500.0, "SUDESTE").getValue("distancia"))
        assertEquals(900.0 * 0.18, FreightCalculator.breakdown(1.0, 900.0, "SUDESTE").getValue("distancia"))
    }

    @Test
    fun `regiao remota e centro-oeste tem taxa propria`() {
        assertEquals(18.90, FreightCalculator.breakdown(1.0, 10.0, "NORTE").getValue("regiao"))
        assertEquals(18.90, FreightCalculator.breakdown(1.0, 10.0, "NORDESTE").getValue("regiao"))
        assertEquals(11.40, FreightCalculator.breakdown(1.0, 10.0, "CENTRO-OESTE").getValue("regiao"))
        assertEquals(5.70, FreightCalculator.breakdown(1.0, 10.0, "SUL").getValue("regiao"))
    }

    @Test
    fun `seguro e tres por cento do restante e o total fecha a soma`() {
        val parts = FreightCalculator.breakdown(12.0, 400.0, "NORTE")
        val semSeguro = parts.getValue("base") + parts.getValue("distancia") + parts.getValue("regiao")

        assertEquals(semSeguro * 0.03, parts.getValue("seguro"))
        assertEquals(semSeguro + parts.getValue("seguro"), parts.getValue("total"))
    }

    @Test
    fun `entrega mais pesada e mais distante custa mais caro`() {
        assertTrue(total(40.0, 1200.0, "NORTE") > total(1.0, 50.0, "SUDESTE"))
    }
}
