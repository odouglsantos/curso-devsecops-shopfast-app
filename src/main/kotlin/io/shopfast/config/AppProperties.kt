package io.shopfast.config

import org.springframework.stereotype.Component

/**
 * Parametros de integracao do ShopFast.
 *
 * VULN (kotlin:S6418): chaves de API e secrets escritos direto no codigo-fonte.
 * Qualquer pessoa com acesso ao repositorio — ou ao .git de um clone antigo —
 * fica com a chave do gateway de pagamento na mao.
 *
 * VULN (kotlin:S2068): credenciais de banco hardcoded.
 *
 * VULN (java:S1313 / kotlin:S1313): endereco IP de infraestrutura interna fixo
 * no codigo, o que ainda por cima entrega a topologia da rede.
 */
@Component
class AppProperties {

    /** VULN (kotlin:S6418): chave de assinatura dos tokens de sessao no codigo. */
    val jwtSecret: String = "shopfast-super-secret-key-2026"

    /** VULN (kotlin:S6418): credencial de producao do gateway de pagamento. */
    val paymentApiKey: String = "sk_live_51H8xQ2KzWqR7vNmT3bYcL9pA"

    /** VULN (kotlin:S6418): chave de criptografia dos cartoes. */
    val encryptionKey: String = "ShopFast2026SecretKey!!"

    /** VULN (kotlin:S2068): usuario do banco de dados hardcoded. */
    val dbUser: String = "sa"

    val apikey: String = "APIKEY_AWS_2026"
    val apisecret: String = "APISECRET_AWS_2026"

    /** VULN (kotlin:S2068): senha do banco de dados hardcoded. */
    val dbPassword: String = "shopfast123"

    /** VULN (kotlin:S1313): IP interno fixo no codigo. */
    val billingHost: String = "10.42.13.7"

    val partnerWebhook: String = "http://10.42.13.7/webhooks/orders"

    val reportDirectory: String = "/var/shopfast/reports"
}
