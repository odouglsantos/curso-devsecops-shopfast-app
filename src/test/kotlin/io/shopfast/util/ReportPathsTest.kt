package io.shopfast.util

import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Lista branca de nome de arquivo e confinamento ao diretorio de relatorios —
 * a correcao do path traversal do Modulo 3.
 */
class ReportPathsTest {

    private val base = "/var/shopfast/reports"

    @Test
    fun `nome simples resolve dentro do diretorio base`() {
        val resolved = ReportPaths.resolveInside(base, "vendas-2026.pdf")

        assertEquals(Path(base).toAbsolutePath().normalize().resolve("vendas-2026.pdf"), resolved)
        assertTrue(resolved.startsWith(Path(base).toAbsolutePath().normalize()))
    }

    @Test
    fun `travessia com ponto-ponto e recusada`() {
        assertFailsWith<IllegalArgumentException> {
            ReportPaths.resolveInside(base, "../../etc/passwd")
        }
    }

    @Test
    fun `barra no nome e recusada`() {
        assertFailsWith<IllegalArgumentException> {
            ReportPaths.resolveInside(base, "sub/dir/relatorio.pdf")
        }
    }

    @Test
    fun `caminho absoluto e recusado`() {
        assertFailsWith<IllegalArgumentException> {
            ReportPaths.resolveInside(base, "/etc/shadow")
        }
    }

    @Test
    fun `nome vazio e recusado`() {
        assertFailsWith<IllegalArgumentException> { ReportPaths.resolveInside(base, "") }
    }

    @Test
    fun `nome longo demais e recusado`() {
        assertFailsWith<IllegalArgumentException> {
            ReportPaths.resolveInside(base, "a".repeat(121))
        }
    }

    @Test
    fun `nome logico valido e devolvido intacto`() {
        assertEquals("vendas_2026-Q1", ReportPaths.requireSafeName("vendas_2026-Q1"))
    }

    @Test
    fun `nome logico com metacaractere de shell e recusado`() {
        assertFailsWith<IllegalArgumentException> {
            ReportPaths.requireSafeName("vendas; rm -rf /")
        }
    }
}
