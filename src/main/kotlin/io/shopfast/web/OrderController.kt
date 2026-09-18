package io.shopfast.web

import io.shopfast.domain.Order
import io.shopfast.service.OrderService
import io.shopfast.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

class CheckoutRequest(
    val items: List<BigDecimal>,
    val couponCode: String?,
    val cardNumber: String,
    val cvv: String,
)

/**
 * Pedidos do ShopFast.
 *
 * O IDOR foi fechado: o dono do pedido e comparado com quem esta autenticado, e
 * o `userId` do checkout vem da sessao, nao do corpo da requisicao — antes
 * bastava trocar o numero no JSON para cobrar no cartao de outra pessoa.
 *
 * Pedido de outro usuario responde **404**, e nao 403: dizer "existe, mas voce
 * nao pode ver" ja entrega quais ids sao validos.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
) {

    @GetMapping("/{id}")
    fun findById(
        @PathVariable("id") id: Long,
        authentication: Authentication,
    ): ResponseEntity<Order> {
        val order = orderService.findById(id) ?: return ResponseEntity.notFound().build()
        if (!ownsOrder(order, authentication)) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(order)
    }

    @PostMapping("/checkout")
    fun checkout(
        @RequestBody request: CheckoutRequest,
        authentication: Authentication,
    ): Map<String, Any> {
        val userId = currentUserId(authentication)
        val total = orderService.calculateTotal(
            items = request.items,
            couponCode = request.couponCode,
            customerTier = "GOLD",
            country = "BR",
            isFirstOrder = false,
            hasSubscription = true,
        )
        val receipt = paymentService.charge(userId, request.cardNumber, request.cvv, total)
        return mapOf("total" to total, "receipt" to receipt)
    }

    @GetMapping("/mine")
    fun listMine(authentication: Authentication): List<Order> =
        orderService.listByUser(currentUserId(authentication))

    private fun ownsOrder(order: Order, authentication: Authentication): Boolean =
        order.userId == currentUserId(authentication) || isAdmin(authentication)

    private fun isAdmin(authentication: Authentication): Boolean =
        authentication.authorities.any { it.authority == "ROLE_ADMIN" }

    private fun currentUserId(authentication: Authentication): Long =
        authentication.name.toLongOrNull()
            ?: throw IllegalStateException("Sessao sem identificacao de usuario")
}
