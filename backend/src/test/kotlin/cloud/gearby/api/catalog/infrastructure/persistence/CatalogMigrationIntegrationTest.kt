package cloud.gearby.api.catalog.infrastructure.persistence

import cloud.gearby.api.support.PostgresIntegrationTest
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
class CatalogMigrationIntegrationTest : PostgresIntegrationTest() {
    @Test
    fun `V8 backfills only published stores and localizes stable category slugs`() {
        val schema = "v8_${UUID.randomUUID().toString().replace("-", "")}"
        migrate(schema, MigrationVersion.fromVersion("7"))

        val publishedId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val draftId = UUID.randomUUID()
        val publishedUpdatedAt = Instant.parse("2025-01-02T03:04:05Z")
        val draftUpdatedAt = Instant.parse("2025-02-03T04:05:06Z")
        connection(schema).use { connection ->
            connection.prepareStatement("UPDATE stores SET updated_at = ? WHERE id = ?").use { statement ->
                statement.setTimestamp(1, Timestamp.from(publishedUpdatedAt))
                statement.setObject(2, publishedId)
                statement.executeUpdate()
            }
            connection
                .prepareStatement(
                    """
                    INSERT INTO stores (id, name, normalized_address, latitude, longitude, status, updated_at)
                    VALUES (?, 'Draft migration fixture', 'Seoul', 37.5, 127.0, 'DRAFT', ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, draftId)
                    statement.setTimestamp(2, Timestamp.from(draftUpdatedAt))
                    statement.executeUpdate()
                }
            connection
                .prepareStatement(
                    """
                    INSERT INTO audit_events (id, actor, action, resource_type, resource_id, created_at)
                    VALUES (?, 'migration-test', 'PUBLISHED', 'STORE', ?, '2030-01-01T00:00:00Z')
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, publishedId)
                    statement.executeUpdate()
                }
        }

        migrate(schema)

        connection(schema).use { connection ->
            assertTrue(
                connection
                    .prepareStatement(
                        """
                        SELECT is_nullable = 'YES'
                        FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'stores' AND column_name = 'verified_at'
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, schema)
                        statement.executeQuery().use { result -> result.next() && result.getBoolean(1) }
                    },
            )
            assertEquals(publishedUpdatedAt, verifiedAt(connection, publishedId))
            assertNull(verifiedAt(connection, draftId))
            assertEquals(
                mapOf("BACKPACKING" to "백패킹", "CAMPING" to "캠핑", "CLIMBING" to "클라이밍", "HIKING" to "등산"),
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT slug, display_name FROM categories ORDER BY slug").use { result ->
                        buildMap {
                            while (result.next()) put(result.getString("slug"), result.getString("display_name"))
                        }
                    }
                },
            )
        }
    }

    private fun migrate(
        schema: String,
        target: MigrationVersion? = null,
    ) {
        val configuration =
            Flyway
                .configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
        target?.let(configuration::target)
        configuration.load().migrate()
    }

    private fun connection(schema: String): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).apply { this.schema = schema }

    private fun verifiedAt(
        connection: Connection,
        id: UUID,
    ): Instant? =
        connection.prepareStatement("SELECT verified_at FROM stores WHERE id = ?").use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { result ->
                result.next()
                result.getTimestamp(1)?.toInstant()
            }
        }
}
