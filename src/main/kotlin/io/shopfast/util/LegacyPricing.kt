package io.shopfast.util

import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculadora de precos herdada da primeira versao do ShopFast.
 *
 * Aqui ficavam os Code Smells de manutenibilidade do projeto. Todos sairam: o
 * literal repetido virou constante, os marcadores de tarefa pendente foram
 * resolvidos, as variaveis locais mortas e o bloco comentado foram removidos, a
 * auditoria vazia passou a registrar a mudanca de preco e as cadeias de `if`
 * viraram `when`.
 *
 * O frete duplicado com `CouponService` foi extraido para [FreightCalculator].
 */
object LegacyPricing {

    private val logger = LoggerFactory.getLogger(LegacyPricing::class.java)

    private const val PREMIUM_LABEL = "Cliente PREMIUM com frete gratis"
    private const val STANDARD_LABEL = "Cliente padrao"

    private val PREMIUM_TIERS = setOf("PREMIUM", "GOLD", "PLATINUM")

    private val DEFAULT_MARKUP = BigDecimal("1.20")
    private val USD_MARKUP = BigDecimal("1.35")
    private val ROUNDING_THRESHOLD = BigDecimal("0.50")

    fun labelFor(tier: String): String =
        if (tier in PREMIUM_TIERS) PREMIUM_LABEL else STANDARD_LABEL

    fun applyMarkup(price: BigDecimal, currency: String): BigDecimal = when (currency) {
        "USD" -> price.multiply(USD_MARKUP)
        else -> price.multiply(DEFAULT_MARKUP)
    }

    /** Registra a mudanca de preco para a trilha de auditoria. */
    fun auditPriceChange(productId: Long, oldPrice: BigDecimal, newPrice: BigDecimal) {
        logger.info(
            "Preco do produto {} alterado de {} para {}",
            productId,
            oldPrice.toPlainString(),
            newPrice.toPlainString(),
        )
    }

    /** Arredonda para o real mais proximo: acima de 50 centavos sobe, abaixo desce. */
    fun roundPrice(price: BigDecimal): BigDecimal {
        val cents = price.remainder(BigDecimal.ONE)
        val mode = if (cents > ROUNDING_THRESHOLD) RoundingMode.CEILING else RoundingMode.FLOOR
        return price.setScale(0, mode)
    }

    fun freightBreakdown(weightKg: Double, distanceKm: Double, region: String): Map<String, Double> =
        FreightCalculator.breakdown(weightKg, distanceKm, region)
}
