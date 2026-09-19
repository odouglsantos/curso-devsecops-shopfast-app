package io.shopfast.web

import io.shopfast.repository.UserRepository
import io.shopfast.service.ReportService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Projecao de usuario sem hash de senha e sem numero de cartao. */
class UserSummary(
    val id: Long?,
    val username: String,
    val email: String,
    val role: String,
)

/**
 * Area administrativa do ShopFast.
 *
 * Toda esta area exige o papel ADMIN — ver `SecurityConfig`, onde
 * `/admin` deixou de ser `permitAll()`. Alem disso:
 *
 * - `/admin/users` devolve uma projecao sem `passwordHash` e sem
 *   `creditCardNumber`. Antes entregava a base inteira, o que o ZAP levantava
 *   como Hash Disclosure;
 * - `/admin/config`, que despejava todos os segredos em claro, foi removido.
 *   Endpoint de diagnostico que imprime chave de producao nao tem uso legitimo;
 * - exportacao, leitura de arquivo, restauracao e importacao delegam para o
 *   `ReportService`, onde command injection, path traversal, desserializacao
 *   insegura e XXE foram fechados.
 */
@RestController
@RequestMapping("/admin")
class AdminController(
    private val userRepository: UserRepository,
    private val reportService: ReportService,
) {

    @GetMapping("/users")
    fun listUsers(): List<UserSummary> = userRepository.findAll().map { user ->
        UserSummary(
            id = user.id,
            username = user.username,
            email = user.email,
            role = user.role,
        )
    }

    @GetMapping("/export")
    fun export(@RequestParam("report") report: String): String = reportService.exportToPdf(report)

    @GetMapping("/file")
    fun readFile(@RequestParam("name") name: String): String = reportService.readReport(name)

    @PostMapping("/restore")
    fun restore(@RequestBody payload: String): Map<String, Any?> =
        reportService.restoreSnapshot(payload)

    @PostMapping("/import")
    fun import(@RequestBody xml: String): Int = reportService.importPartnerReport(xml)
}
