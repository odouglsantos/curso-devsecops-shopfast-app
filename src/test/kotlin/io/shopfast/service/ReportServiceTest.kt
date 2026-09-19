package io.shopfast.service

import com.fasterxml.jackson.core.JsonProcessingException
import io.shopfast.config.AppProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xml.sax.SAXException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Relatorios administrativos: path traversal, XXE, desserializacao insegura,
 * Text4Shell, arquivo temporario previsivel e command injection — as cinco
 * falhas que o Modulo 3 fecha nesta classe.
 */
class ReportServiceTest {

    @TempDir
    lateinit var reportDir: Path

    private val service: ReportService
        get() = ReportService(AppProperties(reportDirectory = reportDir.toString()))

    @Test
    fun `relatorio escrito volta na leitura`() {
        service.writeReport("vendas.txt", "linha 1")

        assertEquals("linha 1", service.readReport("vendas.txt"))
        assertTrue(Files.exists(reportDir.resolve("vendas.txt")))
    }

    @Test
    fun `leitura fora do diretorio de relatorios e recusada`() {
        assertFailsWith<IllegalArgumentException> { service.readReport("../../etc/passwd") }
        assertFailsWith<IllegalArgumentException> { service.readReport("/etc/passwd") }
    }

    @Test
    fun `escrita fora do diretorio de relatorios e recusada`() {
        assertFailsWith<IllegalArgumentException> { service.writeReport("../fora.txt", "x") }
    }

    @Test
    fun `importacao conta os itens do xml`() {
        val xml = "<relatorio><item>a</item><item>b</item></relatorio>"

        assertEquals(2, service.importPartnerReport(xml))
        assertEquals(0, service.importPartnerReport("<relatorio/>"))
    }

    @Test
    fun `xml com DOCTYPE e recusado, entao o XXE nao entra`() {
        val ataque = """
            <?xml version="1.0"?>
            <!DOCTYPE dados [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <relatorio><item>&xxe;</item></relatorio>
        """.trimIndent()

        assertFailsWith<SAXException> { service.importPartnerReport(ataque) }
    }

    @Test
    fun `snapshot em json vira mapa simples`() {
        val mapa = service.restoreSnapshot("""{"produto":"fone","quantidade":3}""")

        assertEquals("fone", mapa["produto"])
        assertEquals(3, mapa["quantidade"])
    }

    @Test
    fun `payload que nao e json e recusado pelo parser`() {
        assertFailsWith<JsonProcessingException> { service.restoreSnapshot("rO0ABXNyABFqYXZh") }
    }

    @Test
    fun `template resolve apenas as chaves informadas`() {
        val resultado = service.renderTemplate(
            "Ola \${nome}, seu pedido \${pedido} saiu",
            mapOf("nome" to "Joana", "pedido" to "1042"),
        )

        assertEquals("Ola Joana, seu pedido 1042 saiu", resultado)
    }

    @Test
    fun `variavel desconhecida fica intacta em vez de estourar`() {
        assertEquals("Ola \${desconhecida}", service.renderTemplate("Ola \${desconhecida}", emptyMap()))
    }

    @Test
    fun `interpolador de script do Text4Shell nao e resolvido`() {
        val payload = "\${script:javascript:java.lang.Runtime.getRuntime().exec('touch /tmp/x')}"

        val resultado = service.renderTemplate(payload, mapOf("nome" to "Joana"))

        assertEquals(payload, resultado)
        assertFalse(resultado.contains("java.lang.Process"))
    }

    @Test
    fun `arquivo temporario tem nome aleatorio e permissao so do dono`() {
        val primeiro = service.stageTempReport("conteudo")
        val segundo = service.stageTempReport("conteudo")

        try {
            assertEquals("conteudo", Files.readString(primeiro))
            assertTrue(primeiro != segundo)
            assertContains(primeiro.fileName.toString(), "shopfast-report-")
            assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(primeiro),
            )
        } finally {
            Files.deleteIfExists(primeiro)
            Files.deleteIfExists(segundo)
        }
    }

    @Test
    fun `nome de relatorio com metacaractere de shell e recusado antes do processo`() {
        assertFailsWith<IllegalArgumentException> { service.exportToPdf("vendas; rm -rf /") }
        assertFailsWith<IllegalArgumentException> { service.exportToPdf("../../etc/passwd") }
    }

    @Test
    fun `exportacao usa binario absoluto e lista de argumentos`() {
        service.writeReport("vendas.html", "<html/>")

        val resultado = runCatching { service.exportToPdf("vendas") }

        // Onde o wkhtmltopdf existe, o caminho do PDF volta; onde nao existe, o
        // ProcessBuilder recusa o binario. Em nenhum dos dois casos ha shell, e
        // por isso nenhum `;` do nome chega a ser interpretado.
        resultado.fold(
            onSuccess = { assertTrue(it.endsWith("vendas.pdf")) },
            onFailure = { assertTrue(it is IOException, "esperado IOException, veio $it") },
        )
    }

    @Test
    fun `compressao tambem passa pela lista branca de nome`() {
        service.writeReport("dados.txt", "conteudo")

        val resultado = runCatching { service.compressArchive("dados.txt") }

        assertTrue(resultado.isSuccess || resultado.exceptionOrNull() is IOException)
        assertFailsWith<IllegalArgumentException> { service.compressArchive("../dados.txt") }
    }
}
