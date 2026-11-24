package no.nav.openinghours.service

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID

@Testcontainers
class ActivityMigrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")
    }

    @Test
    fun `existing activities become manual when migrating from version 15`() {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .target("15")
            .load()
            .migrate()

        val id = UUID.randomUUID()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(
                "INSERT INTO activity (id, header, message, start_at, end_at) VALUES (?, 'header', 'message', now(), now() + interval '1 hour')"
            ).use {
                it.setObject(1, id)
                it.executeUpdate()
            }
        }

        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .load()
            .migrate()

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement("SELECT source FROM activity WHERE id = ?").use {
                it.setObject(1, id)
                it.executeQuery().use { result ->
                    assertThat(result.next()).isTrue()
                    assertThat(result.getString("source")).isEqualTo("MANUAL")
                }
            }
        }
    }
}
