package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.util.CryptoUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Integracao com o gateway de pagamento.
 *
 * O que foi corrigido:
 *
 * - numero do cartao e CVV sairam do log (so os quatro ultimos digitos ficam),
 *   o que era violacao direta de PCI-DSS e LGPD;
 * - a chave de API saiu da query string, onde acabava em log de proxy e no
 *   historico do navegador, e passou a viajar em cabecalho;
 * - o endereco do gateway vem de configuracao, nao de um IP fixo no codigo;
 * - o cartao e cifrado com AES-256/GCM, no lugar de DES/ECB;
 * - o identificador do recibo e um UUID aleatorio, nao um MD5;
 * - `isRefundable` comparava `amount > amount` (sempre falso), os ramos do
 *   `when` de `describeStatus` eram todos iguais, `calculateFee` usava
 *   `3 / 100` — divisao inteira, ou seja, taxa zero — e `installmentValue`
 *   dividia por zero.
 */
@Service
class PaymentService(
    private val notificationService: NotificationService,
    private val properties: AppProperties,
    private val cryptoUtils: CryptoUtils,
) {

    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    fun charge(userId: Long, cardNumber: String, cvv: String, amount: BigDecimal): String {
        logger.info("Cobrando usuario {} cartao {} valor {}", userId, mask(cardNumber), amount)

        val encryptedCard = cryptoUtils.encrypt(cardNumber)
        val encryptedCvv = cryptoUtils.encrypt(cvv)
        val endpoint = "${properties.billingBaseUrl}/v1/charges"
        val payload = """
            {"user":$userId,"card":"$encryptedCard","cvv":"$encryptedCvv","amount":${amount.toPlainString()}}
        """.trimIndent()

        notificationService.post(
            endpoint,
            payload,
            mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${properties.paymentApiKey}",
            ),
        )

        return UUID.randomUUID().toString()
    }

    /** Taxa de 3% sobre o valor cobrado. */
    fun calculateFee(amount: BigDecimal): BigDecimal =
        amount.multiply(FEE_RATE).setScale(SCALE, RoundingMode.HALF_UP)

    fun installmentValue(total: BigDecimal, installments: Int): BigDecimal {
        require(installments > 0) { "Numero de parcelas invalido: $installments" }
        return total.divide(BigDecimal(installments), SCALE, RoundingMode.HALF_UP)
    }

    fun describeStatus(status: String): String = when (status) {
        "APPROVED" -> "Pagamento aprovado"
        "PENDING" -> "Pagamento aguardando confirmacao"
        "DECLINED" -> "Pagamento recusado"
        else -> "Pagamento com status desconhecido"
    }

    /** So ha o que estornar quando o valor cobrado foi positivo. */
    fun isRefundable(amount: BigDecimal): Boolean = amount > BigDecimal.ZERO

    private fun mask(cardNumber: String): String =
        if (cardNumber.length <= VISIBLE_DIGITS) "*".repeat(cardNumber.length)
        else "*".repeat(cardNumber.length - VISIBLE_DIGITS) + cardNumber.takeLast(VISIBLE_DIGITS)

    private companion object {
        private const val SCALE = 2
        private const val VISIBLE_DIGITS = 4
        private val FEE_RATE = BigDecimal("0.03")
    }
}
