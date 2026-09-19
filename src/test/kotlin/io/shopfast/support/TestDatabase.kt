package io.shopfast.support

import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType

/**
 * Banco H2 em memoria com o mesmo `schema.sql` e o mesmo `data.sql` da
 * aplicacao. Usar o esquema real importa aqui: os testes de injecao de SQL so
 * provam alguma coisa se a consulta rodar de verdade contra um banco.
 */
object TestDatabase {

    fun start(name: String): EmbeddedDatabase = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .setName(name)
        .addScript("classpath:schema.sql")
        .addScript("classpath:data.sql")
        .build()
}
