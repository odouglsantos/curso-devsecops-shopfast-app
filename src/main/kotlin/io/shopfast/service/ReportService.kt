package io.shopfast.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.shopfast.config.AppProperties
import io.shopfast.util.ReportPaths
import org.apache.commons.text.StringSubstitutor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Geracao e importacao de relatorios administrativos do ShopFast.
 *
 * Era aqui que moravam as falhas mais graves do Modulo 3 (DAST). Todas fechadas:
 *
 * - **Path traversal** (leitura e escrita): o nome do arquivo passa por lista
 *   branca e o caminho resolvido e conferido contra o diretorio base, em
 *   [ReportPaths];
 * - **Command injection**: o shell saiu de cena. O processo e iniciado com
 *   `ProcessBuilder` e uma lista de argumentos, entao `; rm -rf /` vira apenas
 *   um nome de arquivo invalido — e o nome ja foi validado antes;
 * - **XXE**: o parser recusa DOCTYPE e nao busca recurso externo;
 * - **Desserializacao insegura**: `ObjectInputStream` sobre conteudo do cliente
 *   saiu. O snapshot agora e JSON, lido como mapa, sem tipagem polimorfica —
 *   nao ha gadget chain possivel;
 * - **Text4Shell**: a interpolacao usa apenas o mapa de valores informado, com
 *   substituicao recursiva desligada, entao `${script:...}` nao e resolvido;
 * - **Arquivo temporario previsivel**: `createTempFile` com nome aleatorio e
 *   permissao so do dono, no lugar de um caminho fixo em `/tmp`.
 */
@Service
class ReportService(properties: AppProperties) {

    private val logger = LoggerFactory.getLogger(ReportService::class.java)

    private val reportDirectory = properties.reportDirectory

    private val objectMapper = ObjectMapper()

    fun readReport(fileName: String): String =
        Files.readString(ReportPaths.resolveInside(reportDirectory, fileName))

    fun writeReport(fileName: String, content: String) {
        val target = ReportPaths.resolveInside(reportDirectory, fileName)
        Files.createDirectories(target.parent)
        Files.writeString(target, content)
    }

    /** Parser sem DTD e sem acesso externo: o XXE nao tem por onde entrar. */
    fun importPartnerReport(xml: String): Int {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature(DISALLOW_DOCTYPE, true)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false

        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        return document.getElementsByTagName("item").length
    }

    /**
     * Snapshot em JSON, lido como mapa. Sem `ObjectInputStream`, nao ha
     * instanciacao de classe arbitraria e portanto nao ha RCE por gadget chain.
     */
    fun restoreSnapshot(payload: String): Map<String, Any?> {
        val type = objectMapper.typeFactory
            .constructMapType(LinkedHashMap::class.java, String::class.java, Any::class.java)
        return objectMapper.readValue(payload, type)
    }

    /**
     * Sem shell e com argumentos separados: o nome do relatorio nunca e
     * interpretado como comando.
     */
    fun exportToPdf(reportName: String): String {
        val safeName = ReportPaths.requireSafeName(reportName)
        val source = ReportPaths.resolveInside(reportDirectory, "$safeName.html")
        val target = ReportPaths.resolveInside(reportDirectory, "$safeName.pdf")

        run(listOf(WKHTMLTOPDF, source.toString(), target.toString()))
        return target.toString()
    }

    /** Binario com caminho absoluto: o executavel nao depende do PATH do processo. */
    fun compressArchive(fileName: String) {
        val target = ReportPaths.resolveInside(reportDirectory, fileName)
        run(listOf(GZIP, target.toString()))
    }

    /**
     * Interpolacao restrita ao mapa de valores recebido: sem interpolador
     * padrao, a expansao de `${script:...}` / `${dns:...}` / `${url:...}` do
     * Text4Shell (CVE-2022-42889) nao acontece.
     */
    fun renderTemplate(template: String, values: Map<String, String>): String {
        val substitutor = StringSubstitutor(values)
        substitutor.isEnableSubstitutionInVariables = false
        substitutor.isEnableUndefinedVariableException = false
        return substitutor.replace(template)
    }

    /** Nome aleatorio e permissao `rw-------`: sem corrida por arquivo previsivel. */
    fun stageTempReport(content: String): Path {
        val attributes = PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rw-------"),
        )
        val temp = Files.createTempFile("shopfast-report-", ".tmp", attributes)
        Files.writeString(temp, content)
        return temp
    }

    private fun run(command: List<String>) {
        val process = ProcessBuilder(command).start()
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            logger.error("Comando de relatorio excedeu o tempo limite")
        }
    }

    private companion object {
        private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
        private const val WKHTMLTOPDF = "/usr/bin/wkhtmltopdf"
        private const val GZIP = "/usr/bin/gzip"
        private const val PROCESS_TIMEOUT_SECONDS = 30L
    }
}
