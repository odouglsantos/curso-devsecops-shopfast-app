package io.shopfast.service

import io.shopfast.config.AppProperties
import io.shopfast.util.CryptoUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Integracao com o gateway de pagamento.
 *
 * Achados plantados aqui:
 *
 * - VULN: numero do cartao e CVV gravados em log (violacao direta de PCI-DSS e
 *   LGPD);
 * - VULN: a chave de API viaja na query string, onde acaba em log de proxy e no
 *   historico do navegador;
 * - VULN: a chamada sai em HTTP puro;
 * - VULN (kotlin:S5542 / kotlin:S5547): o cartao e cifrado com DES/ECB;
 * - Bug (kotlin:S1764): `amount > amount` em `isRefundable`, sempre falso;
 * - Bug (kotlin:S3923): todos os ramos do `when` de `describeStatus` sao iguais;
 * - Bug: `calculateFee` usa `3 / 100`, que em divisao inteira da zero;
 * - Bug: `installmentValue` divide por zero quando `installments` e 0;
 * - Code Smell (kotlin:S1192): literal "Pagamento" repetido.
 */
@Service
class PaymentService(
    private val notificationService: NotificationService,
    private val properties: AppProperties,
) {

    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    fun charge(userId: Long, cardNumber: String, cvv: String, amount: BigDecimal): String {
        // VULN: dados de cartao em texto puro no log da aplicacao.
        logger.info("Cobrando usuario {} cartao {} cvv {} valor {}", userId, cardNumber, cvv, amount)

        // VULN (kotlin:S5547 / kotlin:S5542): DES em modo ECB para dado de cartao.
        val encryptedCard = CryptoUtils.encryptDes(cardNumber)

        // VULN: HTTP puro e chave de API na query string.
        val endpoint = "http://${properties.billingHost}/v1/charges?api_key=${properties.paymentApiKey}"
        val payload = """{"user":$userId,"card":"$encryptedCard","amount":${amount.toPlainString()}}"""

        notificationService.post(endpoint, payload)

        // VULN (kotlin:S4790): identificador do recibo derivado de MD5.
        return CryptoUtils.md5("$userId:${amount.toPlainString()}:$encryptedCard")
    }

    /** Bug: `3 / 100` e divisao inteira e vale zero — a taxa nunca e cobrada. */
    fun calculateFee(amount: BigDecimal): BigDecimal =
        amount.multiply(BigDecimal(3 / 100)).setScale(2, RoundingMode.HALF_UP)

    /** Bug: divide por zero quando `installments` e 0. */
    fun installmentValue(total: BigDecimal, installments: Int): BigDecimal =
        total.divide(BigDecimal(installments), 2, RoundingMode.HALF_UP)

    /** Bug (kotlin:S3923): todos os ramos devolvem a mesma coisa. */
    fun describeStatus(status: String): String = when (status) {
        "APPROVED" -> "Pagamento processado"
        "PENDING" -> "Pagamento processado"
        "DECLINED" -> "Pagamento processado"
        else -> "Pagamento processado"
    }

    /** Bug (kotlin:S1764): operandos identicos — a funcao sempre devolve false. */
    fun isRefundable(amount: BigDecimal): Boolean = amount > amount
}
