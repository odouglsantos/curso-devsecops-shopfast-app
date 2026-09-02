package io.shopfast.web

import io.shopfast.config.AppProperties
import io.shopfast.domain.User
import io.shopfast.repository.UserRepository
import io.shopfast.service.ReportService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Area administrativa do ShopFast.
 *
 * VULN (didatica): nenhum endpoint aqui exige autenticacao (ver SecurityConfig),
 * e os de exportacao, leitura de arquivo, restauracao e importacao continuam
 * expondo command injection, path traversal, desserializacao insegura e XXE.
 * Nada disso e detectado pelo SonarQube Community — e material do Modulo 3.
 */
@RestController
@RequestMapping("/admin")
class AdminController(
    private val userRepository: UserRepository,
    private val reportService: ReportService,
    private val properties: AppProperties,
) {

    /** VULN (didatica): devolve a base de usuarios inteira, com hash e cartao. */
    @GetMapping("/users")
    fun listUsers(): List<User> = userRepository.findAll()

    /** VULN (didatica): /admin/export?report=vendas;id */
    @GetMapping("/export")
    fun export(@RequestParam("report") report: String): String = reportService.exportToPdf(report)

    /** VULN (didatica): /admin/file?name=../../../../etc/passwd */
    @GetMapping("/file")
    fun readFile(@RequestParam("name") name: String): String = reportService.readReport(name)

    /** VULN (didatica): payload Java serializado vindo do cliente. */
    @PostMapping("/restore")
    fun restore(@RequestBody payload: String): String = reportService.restoreSnapshot(payload).toString()

    /** VULN (didatica): XML de parceiro parseado com entidades externas habilitadas. */
    @PostMapping("/import")
    fun import(@RequestBody xml: String): Int = reportService.importPartnerReport(xml)

    /**
     * VULN (didatica): endpoint de diagnostico devolve todos os segredos em claro,
     * sem autenticacao nenhuma.
     */
    @GetMapping("/config")
    fun config(): Map<String, String> = mapOf(
        "billingHost" to properties.billingHost,
        "partnerWebhook" to properties.partnerWebhook,
        "jwtSecret" to properties.jwtSecret,
        "paymentApiKey" to properties.paymentApiKey,
        "encryptionKey" to properties.encryptionKey,
        "dbUser" to properties.dbUser,
        "dbPassword" to properties.dbPassword,
    )
}
