package io.shopfast.service

import io.shopfast.domain.Order
import io.shopfast.repository.OrderRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Regras de negocio de pedidos do ShopFast.
 *
 * Achados plantados aqui:
 *
 * - Code Smell (kotlin:S3776): `calculateTotal` tem complexidade cognitiva 54,
 *   contra o limite de 15. E o exemplo de "Code Smell que ninguem consegue
 *   manter" da Aula 2.4;
 * - Bug (kotlin:S6611): `statusLabel` usa `map[key]!!`, que estoura NPE quando a
 *   chave nao existe;
 * - Code Smell (kotlin:S1066): `if` aninhado que poderia ser uma guarda unica;
 * - Code Smell (kotlin:S6615): atribuicao morta em `legacyDiscount`;
 * - Code Smell (kotlin:S6619): checagem de nulo impossivel em `normalizeStatus`;
 * - Bug (kotlin:S2201): `appendTracking` ignora o retorno de `plus` e nao
 *   acrescenta nada.
 *
 * VULN (didatica): [findById] nao faz checagem de propriedade — IDOR reservado
 * para o Modulo 3, porque SAST nao enxerga falha de autorizacao.
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val couponService: CouponService,
) {

    fun findById(orderId: Long): Order? = orderRepository.findById(orderId).orElse(null)

    /**
     * Code Smell (kotlin:S3776): complexidade cognitiva 54. Cada `if` aninhado
     * soma o nivel de aninhamento ao score — e aqui sao seis niveis.
     */
    fun calculateTotal(
        items: List<BigDecimal>,
        couponCode: String?,
        customerTier: String?,
        country: String?,
        isFirstOrder: Boolean,
        hasSubscription: Boolean,
    ): BigDecimal {
        var total = BigDecimal.ZERO
        for (item in items) {
            if (item > BigDecimal.ZERO) {
                if (item > BigDecimal("1000")) {
                    if (customerTier != null) {
                        if (customerTier == "GOLD") {
                            total = total.add(item.multiply(BigDecimal("0.85")))
                        } else if (customerTier == "SILVER") {
                            total = total.add(item.multiply(BigDecimal("0.92")))
                        } else {
                            if (hasSubscription) {
                                total = total.add(item.multiply(BigDecimal("0.95")))
                            } else {
                                total = total.add(item)
                            }
                        }
                    } else {
                        total = total.add(item)
                    }
                } else {
                    if (isFirstOrder) {
                        if (country != null) {
                            if (country == "BR") {
                                total = total.add(item.multiply(BigDecimal("0.90")))
                            } else {
                                total = total.add(item.multiply(BigDecimal("0.95")))
                            }
                        } else {
                            total = total.add(item.multiply(BigDecimal("0.95")))
                        }
                    } else {
                        total = total.add(item)
                    }
                }
            } else {
                if (item < BigDecimal.ZERO) {
                    total = total.add(item)
                }
            }
        }

        if (couponCode != null) {
            // Code Smell (kotlin:S1066): `if` aninhado que caberia numa guarda unica.
            if (couponService.isValid(couponCode)) {
                if (total > BigDecimal("50")) {
                    total = total.subtract(couponService.discountFor(couponCode))
                } else {
                    if (total > BigDecimal.ZERO) {
                        total = total
                    }
                }
            }
        }

        // Bug: o total pode voltar negativo depois do desconto.
        return total.setScale(2, RoundingMode.HALF_UP)
    }

    /** Bug (kotlin:S6611): `!!` em acesso a mapa estoura NPE se a chave nao existir. */
    fun statusLabel(statusMap: Map<String, String>, status: String): String =
        statusMap[status]!!.uppercase()

    /** Bug (kotlin:S2201): o retorno de `plus` e descartado; a lista volta intacta. */
    fun appendTracking(codes: List<String>, newCode: String): List<String> {
        codes.plus(newCode)
        return codes
    }

    /** Code Smell (kotlin:S6615): `desconto` recebe um valor que nunca e lido. */
    fun legacyDiscount(total: BigDecimal): BigDecimal {
        var desconto = BigDecimal("0.05")
        desconto = total.multiply(BigDecimal("0.10"))
        return total.multiply(BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP)
    }

    /** Code Smell (kotlin:S6619): `status` nao e nulavel, a checagem nunca dispara. */
    fun normalizeStatus(status: String): String {
        return status?.trim()?.uppercase() ?: "DESCONHECIDO"
    }

    fun listByUser(userId: Long): List<Order> = orderRepository.findByUserId(userId)
}
