package io.shopfast.service

import io.shopfast.domain.Product
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet

/**
 * Busca de produtos da vitrine do ShopFast.
 *
 * Este e o servico usado na Aula 2.4 para demonstrar SQL Injection.
 */
@Service
class ProductSearchService(private val jdbcTemplate: JdbcTemplate) {

    private val logger = LoggerFactory.getLogger(ProductSearchService::class.java)

    /**
     * VULN (kotlin:S2077 / SQL Injection): a query e montada concatenando o texto
     * digitado pelo usuario. Um termo como `' OR '1'='1` devolve o catalogo inteiro,
     * e `'; DROP TABLE products; --` chega a destruir a tabela.
     */
    fun searchByName(searchTerm: String): List<Product> {
        val query = "SELECT * FROM products WHERE name LIKE '%" + searchTerm + "%'"
        logger.info("Executando busca: {}", query)
        return jdbcTemplate.query(query) { rs, _ -> mapProduct(rs) }
    }

    /**
     * VULN: mesma falha, agora com string template do Kotlin e com ordenacao
     * dinamica vinda direto do request.
     */
    fun searchByCategory(category: String, sortColumn: String): List<Product> {
        val query = "SELECT * FROM products WHERE category = '$category' ORDER BY $sortColumn"
        return jdbcTemplate.query(query) { rs, _ -> mapProduct(rs) }
    }

    /**
     * VULN: filtro de preco tambem concatenado. Alem do SQLi, o `toString()` de um
     * valor nao validado permite injetar subqueries.
     */
    fun searchByPriceRange(min: String, max: String): List<Product> {
        val query = String.format(
            "SELECT * FROM products WHERE price BETWEEN %s AND %s",
            min,
            max,
        )
        return jdbcTemplate.query(query) { rs, _ -> mapProduct(rs) }
    }

    /**
     * Versao correta, usada na aula de correcao do Modulo 2.
     * Mantida aqui para comparacao lado a lado durante a gravacao.
     */
    fun searchByNameSafe(searchTerm: String): List<Product> {
        val query = "SELECT * FROM products WHERE name LIKE ?"
        return jdbcTemplate.query(query, { rs, _ -> mapProduct(rs) }, "%$searchTerm%")
    }

    private fun mapProduct(rs: ResultSet): Product = Product(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        category = rs.getString("category"),
        price = rs.getBigDecimal("price"),
        stockQuantity = rs.getInt("stock_quantity"),
        description = rs.getString("description"),
    )
}
