package io.shopfast.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(nullable = false)
    var status: String = "PENDING",

    @Column(nullable = false)
    var total: BigDecimal = BigDecimal.ZERO,

    @Column(name = "coupon_code")
    var couponCode: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),
)
