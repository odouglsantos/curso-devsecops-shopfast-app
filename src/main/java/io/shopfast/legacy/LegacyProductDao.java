package io.shopfast.legacy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * DAO legado do ShopFast, escrito em Java antes da migracao para Kotlin.
 *
 * <p>Motivo de existir em Java: o analisador Kotlin do SonarQube Community nao possui
 * regras de injecao (SQL, comando, XXE). O analisador Java possui. Por isso o achado de
 * <b>SQL Injection</b> das Aulas 2.4 e 2.6 mora aqui.
 *
 * <p>VULN (java:S2077) x3: as tres consultas abaixo montam o SQL concatenando texto
 * vindo do request. Um termo como {@code ' OR '1'='1} devolve o catalogo inteiro, e
 * {@code '; DROP TABLE products; --} chega a destruir a tabela.
 *
 * <p>VULN (java:S2115 e java:S2068): a URL, o usuario e a senha do banco estao fixos no
 * codigo, em vez de virem do {@code DataSource} gerenciado pelo Spring.
 */
@Repository
public class LegacyProductDao {

    /** VULN (java:S2115 / java:S2068): credenciais de banco hardcoded. */
    private static final String DB_URL = "jdbc:h2:mem:shopfast;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "shopfast123";

    /**
     * VULN (java:S2077): o termo digitado e concatenado direto no texto da query.
     *
     * <p>Reproducao: {@code /api/legacy/search?q=' OR '1'='1}
     */
    public List<String> searchByName(String searchTerm) {
        String query = "SELECT name FROM products WHERE name LIKE '%" + searchTerm + "%'";
        return executeQuery(query);
    }

    /**
     * VULN (java:S2077): categoria e coluna de ordenacao concatenadas, as duas vindas
     * do request sem qualquer validacao.
     */
    public List<String> searchByCategory(String category, String sortColumn) {
        String query = "SELECT name FROM products WHERE category = '" + category
                + "' ORDER BY " + sortColumn;
        return executeQuery(query);
    }

    /** VULN (java:S2077): DELETE montado por concatenacao — injecao com efeito destrutivo. */
    public int deleteByName(String name) {
        String query = "DELETE FROM products WHERE name = '" + name + "'";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                Statement statement = connection.createStatement()) {
            return statement.executeUpdate(query);
        } catch (SQLException e) {
            // Code Smell: excecao impressa em stdout em vez de logada.
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Versao correta, usada na Aula 2.6 para o comparativo lado a lado com
     * {@link #searchByName(String)}.
     */
    public List<String> searchByNameSafe(String searchTerm) {
        String query = "SELECT name FROM products WHERE name LIKE ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                java.sql.PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, "%" + searchTerm + "%");
            List<String> names = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
            return names;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private List<String> executeQuery(String query) {
        List<String> names = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return names;
    }
}
