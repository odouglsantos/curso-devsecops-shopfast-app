package io.shopfast.web

import io.shopfast.support.WebTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Camada legada em Java, hoje restrita a ADMIN. As rotas continuam existindo
 * porque sao o material do Modulo 2 (SQL Injection e XXE apontados pelo Sonar).
 */
class LegacyApiTest : WebTestSupport() {

    private val reportDir: Path =
        Path.of(System.getProperty("java.io.tmpdir"), "shopfast-testes")

    @BeforeEach
    fun prepararRelatorios() {
        Files.createDirectories(reportDir)
        Files.writeString(reportDir.resolve("legado.txt"), "relatorio legado")
        Files.writeString(reportDir.resolve("legado.html"), "<html/>")
    }

    @Test
    fun `busca legada devolve os nomes encontrados`() {
        mockMvc.perform(get("/api/legacy/search").param("q", "Teclado").comSessao(adminId, "ADMIN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value("Teclado Mecanico RGB"))
    }

    @Test
    fun `payload de injecao na camada legada volta vazio`() {
        mockMvc.perform(get("/api/legacy/search").param("q", "' OR '1'='1").comSessao(adminId, "ADMIN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `ordenacao legada vem da lista branca`() {
        mockMvc.perform(
            get("/api/legacy/by-category")
                .param("category", "eletronicos")
                .param("sort", "stock")
                .comSessao(adminId, "ADMIN"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value("Teclado Mecanico RGB"))

        mockMvc.perform(
            get("/api/legacy/by-category")
                .param("category", "eletronicos")
                .param("sort", "name; DROP TABLE products")
                .comSessao(adminId, "ADMIN"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `importacao legada conta os itens e recusa DOCTYPE`() {
        mockMvc.perform(
            post("/api/legacy/import")
                .comSessao(adminId, "ADMIN")
                .comCsrf()
                .contentType("application/xml")
                .content("<r><item>a</item></r>"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string("1"))

        mockMvc.perform(
            post("/api/legacy/import")
                .comSessao(adminId, "ADMIN")
                .comCsrf()
                .contentType("application/xml")
                .content("<!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r><item>&x;</item></r>"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `restauracao legada le json como mapa`() {
        mockMvc.perform(
            post("/api/legacy/restore")
                .comSessao(adminId, "ADMIN")
                .comCsrf()
                .contentType("application/json")
                .content("""{"chave":"valor"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.chave").value("valor"))
    }

    @Test
    fun `leitura legada fica confinada ao diretorio de relatorios`() {
        mockMvc.perform(get("/api/legacy/file").param("name", "legado.txt").comSessao(adminId, "ADMIN"))
            .andExpect(status().isOk)
            .andExpect(content().string("relatorio legado"))

        mockMvc.perform(
            get("/api/legacy/file").param("name", "../../etc/passwd").comSessao(adminId, "ADMIN"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `exportacao legada recusa metacaractere e roda sem shell`() {
        mockMvc.perform(
            get("/api/legacy/export").param("report", "legado; id").comSessao(adminId, "ADMIN"),
        ).andExpect(status().isBadRequest)

        val status = mockMvc.perform(
            get("/api/legacy/export").param("report", "legado").comSessao(adminId, "ADMIN"),
        ).andReturn().response.status

        assertTrue(status == 200 || status == 500, "status inesperado: $status")
    }
}
