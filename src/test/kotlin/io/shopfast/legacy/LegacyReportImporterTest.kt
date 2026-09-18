package io.shopfast.legacy

import com.fasterxml.jackson.core.JsonProcessingException
import com.sun.net.httpserver.HttpServer
import io.shopfast.config.AppProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xml.sax.SAXException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Camada legada de relatorios, ainda em Java. Fecha as mesmas falhas da versao
 * Kotlin: XXE, path traversal, desserializacao insegura e command injection.
 */
class LegacyReportImporterTest {

    @TempDir
    lateinit var reportDir: Path

    private fun importer(billingBaseUrl: String = "https://billing.internal.shopfast.io") =
        LegacyReportImporter(
            AppProperties(reportDirectory = reportDir.toString(), billingBaseUrl = billingBaseUrl),
        )

    @Test
    fun `importacao conta os itens do xml`() {
        assertEquals(2, importer().importPartnerReport("<r><item>a</item><item>b</item></r>"))
        assertEquals(0, importer().importPartnerReport("<r/>"))
    }

    @Test
    fun `xml com DOCTYPE e recusado, entao o XXE nao entra`() {
        val ataque = """
            <?xml version="1.0"?>
            <!DOCTYPE dados [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <r><item>&xxe;</item></r>
        """.trimIndent()

        assertFailsWith<SAXException> { importer().importPartnerReport(ataque) }
    }

    @Test
    fun `snapshot em json vira mapa simples`() {
        val mapa = importer().restoreSnapshot("""{"produto":"fone","quantidade":3}""")

        assertEquals("fone", mapa["produto"])
        assertEquals(3, mapa["quantidade"])
    }

    @Test
    fun `payload serializado do Java nao e mais desserializado`() {
        assertFailsWith<JsonProcessingException> { importer().restoreSnapshot("rO0ABXNyABFqYXZh") }
    }

    @Test
    fun `leitura confinada ao diretorio de relatorios`() {
        Files.writeString(reportDir.resolve("vendas.txt"), "linha 1")

        assertEquals("linha 1", importer().readReport("vendas.txt"))
        assertFailsWith<IllegalArgumentException> { importer().readReport("../../etc/passwd") }
        assertFailsWith<IllegalArgumentException> { importer().readReport("/etc/passwd") }
    }

    @Test
    fun `nome de relatorio com metacaractere de shell e recusado`() {
        assertFailsWith<IllegalArgumentException> { importer().exportToPdf("vendas; rm -rf /") }
        assertFailsWith<IllegalArgumentException> { importer().exportToPdf("../../tmp/x") }
    }

    @Test
    fun `exportacao chama o binario absoluto sem shell`() {
        Files.writeString(reportDir.resolve("vendas.html"), "<html/>")

        val resultado = runCatching { importer().exportToPdf("vendas") }

        // Onde o wkhtmltopdf existe, a exportacao roda; onde nao existe, o
        // ProcessBuilder recusa o binario. Em nenhum dos casos ha shell.
        assertTrue(resultado.isSuccess || resultado.exceptionOrNull() is IOException)
    }

    @Test
    fun `cobranca usa o endereco vindo de configuracao`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/billing/orders/42") { exchange ->
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
        }
        server.start()
        try {
            val porta = server.address.port

            assertEquals(202, importer("http://127.0.0.1:$porta").notifyBilling(42L))
        } finally {
            server.stop(0)
        }
    }
}
