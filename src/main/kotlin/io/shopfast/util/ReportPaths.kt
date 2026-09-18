package io.shopfast.util

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolucao segura de caminho de relatorio, compartilhada pela camada Kotlin
 * (`ReportService`) e pela legada em Java (`LegacyReportImporter`).
 *
 * Concentrar isso aqui evita as duas copias da mesma regra e, principalmente,
 * evita que uma delas fique para tras numa correcao futura.
 */
object ReportPaths {

    /** So letra, numero, ponto, hifen e underline. Barra e `..` ficam de fora. */
    private val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,120}$")

    /**
     * Resolve [fileName] dentro de [baseDirectory] e confirma que o resultado
     * nao escapou do diretorio.
     *
     * `normalize()` sozinho nao basta: um link simbolico dentro do diretorio
     * ainda apontaria para fora. Por isso a checagem e feita no caminho
     * absoluto normalizado, e o nome passa antes pela lista branca.
     */
    @JvmStatic
    fun resolveInside(baseDirectory: String, fileName: String): Path {
        require(SAFE_NAME.matches(fileName)) { "Nome de arquivo invalido" }

        val base = Paths.get(baseDirectory).toAbsolutePath().normalize()
        val resolved = base.resolve(fileName).normalize()
        require(resolved.startsWith(base)) { "Caminho fora do diretorio de relatorios" }
        return resolved
    }

    /** Valida o nome logico de um relatorio (sem extensao). */
    @JvmStatic
    fun requireSafeName(reportName: String): String {
        require(SAFE_NAME.matches(reportName)) { "Nome de relatorio invalido" }
        return reportName
    }
}
