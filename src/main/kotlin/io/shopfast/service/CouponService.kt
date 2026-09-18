package io.shopfast.service

import io.shopfast.util.FreightCalculator
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Emissao e validacao de cupons promocionais.
 *
 * O `java.util.Random(42)` — semente fixa, sequencia inteira reproduzivel por
 * quem conhecesse a semente — deu lugar a [SecureRandom]. E a validacao deixou
 * de aceitar qualquer codigo comecado com "SHOP": agora so vale o que foi
 * realmente emitido.
 *
 * O calculo de frete, que era uma copia literal do de `LegacyPricing`, mora em
 * [FreightCalculator].
 */
@Service
class CouponService {

    private val random = SecureRandom()

    private val issued = ConcurrentHashMap<String, BigDecimal>()

    fun issueCoupon(value: BigDecimal): String {
        val code = CODE_PREFIX + random.nextInt(CODE_RANGE).toString().padStart(CODE_DIGITS, '0')
        issued[code] = value
        return code
    }

    /** Valido apenas o cupom que o proprio servico emitiu. */
    fun isValid(code: String): Boolean = issued.containsKey(code)

    fun discountFor(code: String): BigDecimal = issued[code] ?: BigDecimal.ZERO

    fun freightBreakdown(weightKg: Double, distanceKm: Double, region: String): Map<String, Double> =
        FreightCalculator.breakdown(weightKg, distanceKm, region)

    private companion object {
        private const val CODE_PREFIX = "SHOP"
        private const val CODE_RANGE = 1_000_000
        private const val CODE_DIGITS = 6
    }
}
