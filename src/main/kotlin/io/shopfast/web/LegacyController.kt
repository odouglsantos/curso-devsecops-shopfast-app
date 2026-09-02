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
 * E por aqui que se reproduz, na pratica, o SQL Injection demonstrado na Aula 2.4.
 */
@RestController
@RequestMapping("/api/legacy")
class LegacyController(
    private val legacyProductDao: LegacyProductDao,
    private val legacyReportImporter: LegacyReportImporter,
) {

    /** Reproducao: /api/legacy/search?q=' OR '1'='1 */
    @GetMapping("/search")
    fun search(@RequestParam("q") term: String): List<String> = legacyProductDao.searchByName(term)

    @GetMapping("/by-category")
    fun byCategory(
        @RequestParam("category") category: String,
        @RequestParam("sort", defaultValue = "name") sort: String,
    ): List<String> = legacyProductDao.searchByCategory(category, sort)

    /** Reproducao: /api/legacy/import com um XML contendo ENTITY SYSTEM "file:///etc/passwd" */
    @PostMapping("/import")
    fun import(@RequestBody xml: String): Int = legacyReportImporter.importPartnerReport(xml)

    @PostMapping("/restore")
    fun restore(@RequestBody payload: String): String =
        legacyReportImporter.restoreSnapshot(payload).toString()

    @GetMapping("/file")
    fun readFile(@RequestParam("name") name: String): String = legacyReportImporter.readReport(name)

    @GetMapping("/export")
    fun export(@RequestParam("report") report: String) = legacyReportImporter.exportToPdf(report)
}
