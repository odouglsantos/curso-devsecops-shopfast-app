package io.shopfast.web

import io.shopfast.legacy.LegacyProductDao
import io.shopfast.legacy.LegacyReportImporter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Expoe a camada legada em Java, que ainda nao foi migrada para Kotlin.
 *
 * Passou a exigir o papel ADMIN (ver `SecurityConfig`): sao rotas internas de
 * manutencao, sem razao para ficarem publicas.
 */
@RestController
@RequestMapping("/api/legacy")
class LegacyController(
    private val legacyProductDao: LegacyProductDao,
    private val legacyReportImporter: LegacyReportImporter,
) {

    @GetMapping("/search")
    fun search(@RequestParam("q") term: String): List<String> = legacyProductDao.searchByName(term)

    @GetMapping("/by-category")
    fun byCategory(
        @RequestParam("category") category: String,
        @RequestParam("sort", defaultValue = "name") sort: String,
    ): List<String> = legacyProductDao.searchByCategory(category, sort)

    @PostMapping("/import")
    fun import(@RequestBody xml: String): Int = legacyReportImporter.importPartnerReport(xml)

    @PostMapping("/restore")
    fun restore(@RequestBody payload: String): Map<String, Any> =
        legacyReportImporter.restoreSnapshot(payload)

    @GetMapping("/file")
    fun readFile(@RequestParam("name") name: String): String = legacyReportImporter.readReport(name)

    @GetMapping("/export")
    fun export(@RequestParam("report") report: String) = legacyReportImporter.exportToPdf(report)
}
