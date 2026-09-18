package io.shopfast.web

import io.shopfast.support.WebTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Area administrativa. Alem de passar a exigir ADMIN, `/admin/users` virou
 * projecao sem hash e sem cartao, e `/admin/config` — que despejava os segredos
 * em claro — foi removido.
 */
class AdminApiTest : WebTestSupport() {

    private val reportDir: Path =
        Path.of(System.getProperty("java.io.tmpdir"), "shopfast-testes")

    @BeforeEach
    fun prepararRelatorios() {
        Files.createDirectories(reportDir)
        Files.writeString(reportDir.resolve("vendas.txt"), "total: 1042")
        Files.writeString(reportDir.resolve("vendas.html"), "<html/>")
    }

    @Test
    fun `listagem de usuarios nao expoe hash nem cartao`() {
        val corpo = mockMvc.perform(get("/admin/users").comSessao(adminId, "ADMIN"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].username").value("admin"))
            .andExpect(jsonPath("$[0].role").value("ADMIN"))
            .andReturn().response.contentAsString

        assertFalse(corpo.contains("passwordHash"))
        assertFalse(corpo.contains("creditCardNumber"))
        assertFalse(corpo.contains("\$2a\$"))
        assertFalse(corpo.contains("4111111111111111"))
    }

    @Test
    fun `leitura de arquivo fica confinada ao diretorio de relatorios`() {
        mockMvc.perform(get("/admin/file").param("name", "vendas.txt").comSessao(adminId, "ADMIN"))
            .andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("total: 1042"))

        mockMvc.perform(
            get("/admin/file").param("name", "../../etc/passwd").comSessao(adminId, "ADMIN"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("requisicao invalida"))
    }

    @Test
    fun `arquivo inexistente vira erro generico, sem caminho interno na resposta`() {
        val corpo = mockMvc.perform(
            get("/admin/file").param("name", "nao-existe.txt").comSessao(adminId, "ADMIN"),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("erro interno"))
            .andReturn().response.contentAsString

        assertFalse(corpo.contains("java.nio"))
        assertFalse(corpo.contains(reportDir.toString()))
    }

    @Test
    fun `restauracao de snapshot le json como mapa`() {
        mockMvc.perform(
            post("/admin/restore")
                .comSessao(adminId, "ADMIN")
                .comCsrf()
                .contentType("application/json")
                .content("""{"produto":"fone","quantidade":3}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.produto").value("fone"))
            .andExpect(jsonPath("$.quantidade").value(3))
    }

    @Test
    fun `payload serializado do Java e recusado com 400`() {
        mockMvc.perform(
            post("/admin/restore")
                .comSessao(adminId, "ADMIN")
                .comCsrf()
                .contentType("application/json")
                .content("rO0ABXNyABFqYXZhLnV0aWwuSGFzaE1hcA"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `importacao conta os itens do xml`() {
        mockMvc.perform(
            post("/admin/import")
                .comSessao(adminId, "ADMIN")
                .comCsrf()
                .contentType("application/xml")
                .content("<relatorio><item>a</item><item>b</item></relatorio>"),
        )
            .andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("2"))
    }

    @Test
    fun `xml com DOCTYPE e recusado com 400, sem ler arquivo do servidor`() {
        val ataque = """
            <?xml version="1.0"?>
            <!DOCTYPE dados [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <relatorio><item>&xxe;</item></relatorio>
        """.trimIndent()

        val corpo = mockMvc.perform(
            post("/admin/import")
                .comSessao(adminId, "ADMIN")
                .comCsrf()
                .contentType("application/xml")
                .content(ataque),
        )
            .andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        assertContains(corpo, "conteudo invalido")
        assertFalse(corpo.contains("root:"))
    }

    @Test
    fun `nome de relatorio com metacaractere de shell e recusado`() {
        mockMvc.perform(
            get("/admin/export").param("report", "vendas; cat /etc/passwd").comSessao(adminId, "ADMIN"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `exportacao roda sem shell`() {
        val status = mockMvc.perform(
            get("/admin/export").param("report", "vendas").comSessao(adminId, "ADMIN"),
        ).andReturn().response.status

        // 200 onde o wkhtmltopdf existe; 500 onde o binario falta. Nunca 400,
        // porque o nome passou pela lista branca, e nunca shell.
        assertTrue(status == 200 || status == 500, "status inesperado: $status")
    }

    @Test
    fun `endpoint que despejava os segredos nao existe mais`() {
        mockMvc.perform(get("/admin/config").comSessao(adminId, "ADMIN"))
            .andExpect(status().isNotFound)
    }
}
