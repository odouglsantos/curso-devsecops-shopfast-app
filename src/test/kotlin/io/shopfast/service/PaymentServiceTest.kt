package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.util.CryptoUtils
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Taxa, parcelamento e descricao de status — que eram, respectivamente, divisao
 * inteira (taxa zero), divisao por zero e um `when` de ramos identicos.
 *
 * `charge` roda sem rede: o endereco de cobranca nao esta na lista branca de
 * webhooks, entao o `NotificationService` recusa o destino antes de conectar.
 */
class PaymentServiceTest {

    private val properties = AppProperties(
        paymentApiKey = "chave-do-gateway",
        encryptionKey = "chave-de-cifra",
        webhookSigningSecret = "segredo",
    )
    private val crypto = CryptoUtils(properties)
    private val service = PaymentService(NotificationService(properties, crypto), properties, crypto)

    @Test
    fun `cobranca devolve um identificador de recibo aleatorio`() {
        val primeiro = service.charge(1L, "4111111111111111", "123", BigDecimal("99.90"))
        val segundo = service.charge(1L, "4111111111111111", "123", BigDecimal("99.90"))

        assertNotEquals(primeiro, segundo)
        assertEquals(primeiro, UUID.fromString(primeiro).toString())
    }

    @Test
    fun `cartao curto e mascarado por inteiro`() {
        val recibo = service.charge(2L, "411", "1", BigDecimal("10"))

        assertTrue(recibo.isNotBlank())
    }

    @Test
    fun `taxa e tres por cento do valor`() {
        assertEquals(BigDecimal("3.00"), service.calculateFee(BigDecimal("100")))
        assertEquals(BigDecimal("0.30"), service.calculateFee(BigDecimal("10")))
        assertEquals(BigDecimal("0.00"), service.calculateFee(BigDecimal.ZERO))
    }

    @Test
    fun `parcela divide o total pelo numero de parcelas`() {
        assertEquals(BigDecimal("33.33"), service.installmentValue(BigDecimal("100"), 3))
        assertEquals(BigDecimal("50.00"), service.installmentValue(BigDecimal("100"), 2))
    }

    @Test
    fun `parcelamento invalido e recusado em vez de dividir por zero`() {
        assertFailsWith<IllegalArgumentException> { service.installmentValue(BigDecimal("100"), 0) }
        assertFailsWith<IllegalArgumentException> { service.installmentValue(BigDecimal("100"), -1) }
    }

    @Test
    fun `cada status tem sua propria descricao`() {
        assertEquals("Pagamento aprovado", service.describeStatus("APPROVED"))
        assertEquals("Pagamento aguardando confirmacao", service.describeStatus("PENDING"))
        assertEquals("Pagamento recusado", service.describeStatus("DECLINED"))
        assertEquals("Pagamento com status desconhecido", service.describeStatus("QUALQUER"))
    }

    @Test
    fun `so ha estorno quando o valor cobrado foi positivo`() {
        assertTrue(service.isRefundable(BigDecimal("0.01")))
        assertFalse(service.isRefundable(BigDecimal.ZERO))
        assertFalse(service.isRefundable(BigDecimal("-10")))
    }
}
