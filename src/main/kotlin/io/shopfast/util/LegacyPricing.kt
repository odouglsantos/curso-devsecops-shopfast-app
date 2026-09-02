package io.shopfast.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculadora de precos herdada da primeira versao do ShopFast.
 *
 * Concentra os Code Smells de manutenibilidade da Aula 2.4:
 * literal repetido (kotlin:S1192), marcadores de tarefa pendente
 * (kotlin:S1135 e kotlin:S1134), variaveis locais mortas (kotlin:S1481),
 * funcao vazia (kotlin:S1186), bloco de codigo comentado (kotlin:S125) e
 * cadeias de `if` que deveriam ser `when` (kotlin:S6511).
 *
 * Tambem duplica, linha a linha, o `freightBreakdown` de
 * `io.shopfast.service.CouponService` (achado de duplicacao / CPD).
 */
object LegacyPricing {

    // TODO: migrar esta classe para o novo motor de precos (kotlin:S1135)
    // FIXME: o arredondamento nao bate com o do financeiro (kotlin:S1134)

    fun labelFor(tier: String): String {
        // VULN de manutencao (kotlin:S1192): o mesmo literal repetido tres vezes.
        if (tier == "PREMIUM") {
            return "Cliente PREMIUM com frete gratis"
        } else if (tier == "GOLD") {
            return "Cliente PREMIUM com frete gratis"
        } else if (tier == "PLATINUM") {
            return "Cliente PREMIUM com frete gratis"
        }
        return "Cliente padrao"
    }

    /** Code Smell (kotlin:S1481): `desconto` e `taxa` nunca sao usadas. */
    fun applyMarkup(price: BigDecimal, currency: String): BigDecimal {
        val desconto = BigDecimal("0.05")
        val taxa = BigDecimal("1.02")
        if (currency == "BRL") {
            return price.multiply(BigDecimal("1.20"))
        } else if (currency == "USD") {
            return price.multiply(BigDecimal("1.35"))
        }
        return price.multiply(BigDecimal("1.20"))
    }

    /** Code Smell (kotlin:S1186): funcao vazia — a auditoria nunca foi implementada. */
    fun auditPriceChange(productId: Long, oldPrice: BigDecimal, newPrice: BigDecimal) {
    }

    fun roundPrice(price: BigDecimal): BigDecimal {
        val cents = price.remainder(BigDecimal.ONE)
        // Code Smell (kotlin:S125): implementacao anterior deixada comentada.
        // if (cents.compareTo(BigDecimal("0.50")) > 0) {
        //     return price.setScale(0, RoundingMode.CEILING)
        // } else {
        //     return price.setScale(0, RoundingMode.FLOOR)
        // }
        if (cents > BigDecimal("0.50")) {
            return price.setScale(0, RoundingMode.CEILING)
        }
        return price.setScale(0, RoundingMode.FLOOR)
    }

    /**
     * Code Smell (duplicacao): bloco identico ao de `CouponService.freightBreakdown`.
     */
    fun freightBreakdown(weightKg: Double, distanceKm: Double, region: String): Map<String, Double> {
        var base = 12.50
        if (weightKg > 30.0) {
            base += 25.0
        } else if (weightKg > 10.0) {
            base += 12.0
        } else if (weightKg > 5.0) {
            base += 6.0
        }
        var distanceRate = 0.08
        if (distanceKm > 800.0) {
            distanceRate = 0.18
        } else if (distanceKm > 300.0) {
            distanceRate = 0.12
        }
        val distanceFee = distanceKm * distanceRate
        var regionFee = 5.70
        if (region == "NORTE" || region == "NORDESTE") {
            regionFee = 18.90
        } else if (region == "CENTRO-OESTE") {
            regionFee = 11.40
        }
        val insurance = (base + distanceFee + regionFee) * 0.03
        val total = base + distanceFee + regionFee + insurance
        return mapOf(
            "base" to base,
            "distancia" to distanceFee,
            "regiao" to regionFee,
            "seguro" to insurance,
            "total" to total,
        )
    }
}
