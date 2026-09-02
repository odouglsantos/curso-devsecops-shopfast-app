package io.shopfast.web

import io.shopfast.domain.Order
import io.shopfast.service.OrderService
import io.shopfast.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

class CheckoutRequest(
    val userId: Long,
    val items: List<BigDecimal>,
    val couponCode: String?,
    val cardNumber: String,
    val cvv: String,
)

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
) {

    /**
     * VULN (IDOR): qualquer pessoa consulta qualquer pedido apenas trocando o id,
     * porque nao existe checagem de propriedade nem autenticacao.
     */
    @GetMapping("/{id}")
    fun findById(@PathVariable("id") id: Long): ResponseEntity<Order> {
        val order = orderService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(order)
    }

    /**
     * VULN: o userId do dono do pedido vem do corpo da requisicao, e nao da sessao.
     */
    @PostMapping("/checkout")
    fun checkout(@RequestBody request: CheckoutRequest): Map<String, Any> {
        val total = orderService.calculateTotal(
            items = request.items,
            couponCode = request.couponCode,
            customerTier = "GOLD",
            country = "BR",
            isFirstOrder = false,
            hasSubscription = true,
        )
        val receipt = paymentService.charge(request.userId, request.cardNumber, request.cvv, total)
        return mapOf("total" to total, "receipt" to receipt)
    }

    @GetMapping("/user/{userId}")
    fun listByUser(@PathVariable("userId") userId: Long): List<Order> = orderService.listByUser(userId)
}
