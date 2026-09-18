package io.shopfast.legacy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * DAO legado do ShopFast, escrito em Java antes da migracao para Kotlin.
 *
 * <p>Era o achado central de <b>SQL Injection</b> (java:S2077) do projeto: as consultas
 * montavam o SQL concatenando texto vindo do request, e {@code ' OR '1'='1} devolvia o
 * catalogo inteiro. Correcao aplicada:
 *
 * <ul>
 *   <li>todo valor vindo do usuario passa por {@link PreparedStatement}, entao o texto
 *       digitado e tratado como dado e nunca como sintaxe SQL;
 *   <li>a coluna de ordenacao nao pode ser parametrizada em SQL, entao virou lista
 *       branca: o que o request manda so e aceito se for chave de {@link #SORT_COLUMNS};
 *   <li>a conexao vem do {@link DataSource} do Spring, e nao de URL, usuario e senha
 *       fixos no codigo (java:S2115 / java:S2068);
 *   <li>a excecao vai para o log, em vez de {@code printStackTrace} no stdout.
 * </ul>
 */
@Repository
public class LegacyProductDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyProductDao.class);

    /** Lista branca de ordenacao: a chave vem do request, o SQL e escrito aqui. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "SELECT name FROM products WHERE category = ? ORDER BY name",
            "price", "SELECT name FROM products WHERE category = ? ORDER BY price",
            "stock", "SELECT name FROM products WHERE category = ? ORDER BY stock_quantity");

    private static final String DEFAULT_SORT = "name";

    private final DataSource dataSource;

    public LegacyProductDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** O termo digitado vai como parametro, nunca concatenado ao texto da query. */
    public List<String> searchByName(String searchTerm) {
        String query = "SELECT name FROM products WHERE name LIKE ?";
        return executeQuery(query, "%" + searchTerm + "%");
    }

    /**
     * A categoria vai como parametro. A coluna de ordenacao, que o SQL nao permite
     * parametrizar, e resolvida pela lista branca {@link #SORT_COLUMNS}.
     */
    public List<String> searchByCategory(String category, String sortColumn) {
        String query = SORT_COLUMNS.get(sortColumn);
        if (query == null) {
            LOGGER.warn("Ordenacao nao permitida; usando '{}'", DEFAULT_SORT);
            query = SORT_COLUMNS.get(DEFAULT_SORT);
        }
        return executeQuery(query, category);
    }

    /** DELETE parametrizado: o nome vindo do request nao consegue alterar a query. */
    public int deleteByName(String name) {
        String query = "DELETE FROM products WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, name);
            return statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Falha ao remover produto", e);
            return 0;
        }
    }

    private List<String> executeQuery(String query, String parameter) {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Falha ao consultar produtos", e);
        }
        return names;
    }
}
