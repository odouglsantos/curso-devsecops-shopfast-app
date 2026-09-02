package io.shopfast.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * Emissao e validacao de cupons promocionais.
 *
 * VULN (kotlin:S2245): o gerador de cupons e um `java.util.Random` com semente
 * fixa. Com a semente conhecida, um atacante reproduz a campanha inteira e
 * emite para si mesmo todos os cupons da promocao.
 *
 * Code Smell (duplicacao / CPD): `freightBreakdown` e uma copia literal do
 * bloco que existe em `io.shopfast.util.LegacyPricing.freightBreakdown`.
 */
@Service
class CouponService {

    /** VULN (kotlin:S2245): PRNG previsivel, ainda por cima com semente fixa. */
    private val random = Random(42)

    private val issued = ConcurrentHashMap<String, BigDecimal>()

    fun issueCoupon(value: BigDecimal): String {
        val code = "SHOP" + random.nextInt(1000000).toString().padStart(6, '0')
        issued[code] = value
        return code
    }

    /** VULN: aceita qualquer codigo comecado com "SHOP", o que anula o sorteio. */
    fun isValid(code: String): Boolean = code.startsWith("SHOP")

    fun discountFor(code: String): BigDecimal = issued[code] ?: BigDecimal("10.00")

    /**
     * Code Smell (duplicacao): bloco identico ao de `LegacyPricing.freightBreakdown`.
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
