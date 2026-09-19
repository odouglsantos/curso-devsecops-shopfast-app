package io.shopfast.service

import io.shopfast.domain.Order
import io.shopfast.repository.OrderRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Regras de negocio de pedidos do ShopFast.
 *
 * O que foi corrigido:
 *
 * - `calculateTotal` tinha complexidade cognitiva 54, contra o limite de 15.
 *   Os seis niveis de `if` aninhado viraram [priceOf], [highTicketPrice],
 *   [standardPrice] e [applyCoupon], sem mudar a regra de preco;
 * - `statusLabel` usava `map[key]!!`, que estourava NPE com chave ausente;
 * - `appendTracking` descartava o retorno de `plus` e devolvia a lista intacta;
 * - `legacyDiscount` tinha atribuicao morta e `normalizeStatus`, checagem de
 *   nulo impossivel;
 * - o total nao volta mais negativo depois do cupom.
 *
 * VULN (didatica): [findById] continua sem checagem de propriedade — o IDOR e
 * material do Modulo 3, porque SAST nao enxerga falha de autorizacao.
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val couponService: CouponService,
) {

    fun findById(orderId: Long): Order? = orderRepository.findById(orderId).orElse(null)

    fun calculateTotal(
        items: List<BigDecimal>,
        couponCode: String?,
        customerTier: String?,
        country: String?,
        isFirstOrder: Boolean,
        hasSubscription: Boolean,
    ): BigDecimal {
        val subtotal = items.fold(BigDecimal.ZERO) { total, item ->
            total.add(priceOf(item, customerTier, country, isFirstOrder, hasSubscription))
        }
        return applyCoupon(subtotal, couponCode)
            .coerceAtLeast(BigDecimal.ZERO)
            .setScale(SCALE, RoundingMode.HALF_UP)
    }

    /** Item negativo entra como esta (estorno); item zerado nao altera o total. */
    private fun priceOf(
        item: BigDecimal,
        customerTier: String?,
        country: String?,
        isFirstOrder: Boolean,
        hasSubscription: Boolean,
    ): BigDecimal = when {
        item <= BigDecimal.ZERO -> item
        item > HIGH_TICKET_THRESHOLD -> highTicketPrice(item, customerTier, hasSubscription)
        else -> standardPrice(item, country, isFirstOrder)
    }

    /** Acima de mil reais vale o desconto do nivel do cliente. */
    private fun highTicketPrice(
        item: BigDecimal,
        customerTier: String?,
        hasSubscription: Boolean,
    ): BigDecimal {
        val factor = when (customerTier) {
            null -> BigDecimal.ONE
            "GOLD" -> GOLD_FACTOR
            "SILVER" -> SILVER_FACTOR
            else -> if (hasSubscription) SUBSCRIPTION_FACTOR else BigDecimal.ONE
        }
        return item.multiply(factor)
    }

    /** Ate mil reais so o primeiro pedido tem desconto, maior no Brasil. */
    private fun standardPrice(item: BigDecimal, country: String?, isFirstOrder: Boolean): BigDecimal {
        if (!isFirstOrder) {
            return item
        }
        val factor = if (country == "BR") FIRST_ORDER_BR_FACTOR else FIRST_ORDER_FACTOR
        return item.multiply(factor)
    }

    /** O cupom so vale acima do piso, e apenas se tiver sido emitido de fato. */
    private fun applyCoupon(total: BigDecimal, couponCode: String?): BigDecimal {
        if (couponCode == null || !couponService.isValid(couponCode) || total <= COUPON_MINIMUM) {
            return total
        }
        return total.subtract(couponService.discountFor(couponCode))
    }

    fun statusLabel(statusMap: Map<String, String>, status: String): String =
        statusMap[status]?.uppercase() ?: UNKNOWN_STATUS

    fun appendTracking(codes: List<String>, newCode: String): List<String> = codes + newCode

    fun legacyDiscount(total: BigDecimal): BigDecimal =
        total.multiply(LEGACY_DISCOUNT_RATE).setScale(SCALE, RoundingMode.HALF_UP)

    fun normalizeStatus(status: String): String = status.trim().uppercase()

    fun listByUser(userId: Long): List<Order> = orderRepository.findByUserId(userId)

    private companion object {
        private const val SCALE = 2
        private const val UNKNOWN_STATUS = "DESCONHECIDO"
        private val HIGH_TICKET_THRESHOLD = BigDecimal("1000")
        private val COUPON_MINIMUM = BigDecimal("50")
        private val GOLD_FACTOR = BigDecimal("0.85")
        private val SILVER_FACTOR = BigDecimal("0.92")
        private val SUBSCRIPTION_FACTOR = BigDecimal("0.95")
        private val FIRST_ORDER_BR_FACTOR = BigDecimal("0.90")
        private val FIRST_ORDER_FACTOR = BigDecimal("0.95")
        private val LEGACY_DISCOUNT_RATE = BigDecimal("0.10")
    }
}
