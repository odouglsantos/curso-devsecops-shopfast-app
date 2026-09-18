package io.shopfast.service

import io.shopfast.domain.Product
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.sql.ResultSet

/**
 * Busca de produtos da vitrine do ShopFast.
 *
 * Este servico era o SQL Injection do Modulo 3 (DAST): as tres consultas
 * montavam a query concatenando o texto do request, e o analisador Kotlin do
 * SonarQube Community nao tem regra de injecao para apontar isso.
 *
 * Correcao: todo valor do usuario vai como parametro (`?`), a coluna de
 * ordenacao — que o SQL nao deixa parametrizar — passa por lista branca, e o
 * filtro de preco e convertido para [BigDecimal] antes de chegar ao banco, o
 * que rejeita qualquer coisa que nao seja numero.
 */
@Service
class ProductSearchService(private val jdbcTemplate: JdbcTemplate) {

    fun searchByName(searchTerm: String): List<Product> {
        val query = "SELECT * FROM products WHERE name LIKE ?"
        return jdbcTemplate.query(query, { rs, _ -> mapProduct(rs) }, "%$searchTerm%")
    }

    /** A ordenacao vem da lista branca; a categoria, como parametro. */
    fun searchByCategory(category: String, sortColumn: String): List<Product> {
        val query = SORT_QUERIES[sortColumn] ?: SORT_QUERIES.getValue(DEFAULT_SORT)
        return jdbcTemplate.query(query, { rs, _ -> mapProduct(rs) }, category)
    }

    /**
     * `min` e `max` chegam como texto do request; converter para [BigDecimal]
     * antes de consultar rejeita subquery e qualquer outra coisa que nao seja
     * numero, e o valor segue como parametro.
     */
    fun searchByPriceRange(min: String, max: String): List<Product> {
        val minPrice = min.toBigDecimalOrNull()
        val maxPrice = max.toBigDecimalOrNull()
        require(minPrice != null && maxPrice != null) { "Faixa de preco invalida" }

        val query = "SELECT * FROM products WHERE price BETWEEN ? AND ?"
        return jdbcTemplate.query(query, { rs, _ -> mapProduct(rs) }, minPrice, maxPrice)
    }

    private fun mapProduct(rs: ResultSet): Product = Product(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        category = rs.getString("category"),
        price = rs.getBigDecimal("price"),
        stockQuantity = rs.getInt("stock_quantity"),
        description = rs.getString("description"),
    )

    private companion object {
        private const val DEFAULT_SORT = "name"
        private val SORT_QUERIES = mapOf(
            "name" to "SELECT * FROM products WHERE category = ? ORDER BY name",
            "price" to "SELECT * FROM products WHERE category = ? ORDER BY price",
            "stock" to "SELECT * FROM products WHERE category = ? ORDER BY stock_quantity",
        )
    }
}
