package cloud.gearby.api.catalog.infrastructure.persistence

import cloud.gearby.api.support.PostgresIntegrationTest
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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

    @Test
    fun `V9 creates candidate ingestion policy run and provenance tables`() {
        val schema = "v9_${UUID.randomUUID().toString().replace("-", "")}"
        migrate(schema)

        connection(schema).use { connection ->
            assertEquals(
                setOf("candidate_ingestion_provider_policy", "candidate_ingestion_runs", "store_candidate_provenance"),
                tableNames(
                    connection,
                    schema,
                ).filter { it.startsWith("candidate_ingestion") || it == "store_candidate_provenance" }.toSet(),
            )
            assertTrue(hasColumn(connection, schema, "candidate_ingestion_provider_policy", "precision_threshold"))
            assertTrue(hasColumn(connection, schema, "candidate_ingestion_runs", "idempotency_key"))
            assertTrue(hasColumn(connection, schema, "store_candidate_provenance", "payload_sha256_digest"))
            assertEquals(
                "candidate_ingestion_runs",
                connection
                    .prepareStatement(
                        """
                        SELECT ccu.table_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
                        WHERE tc.table_schema = ?
                          AND tc.table_name = 'store_candidate_provenance'
                          AND tc.constraint_type = 'FOREIGN KEY'
                          AND ccu.column_name = 'id'
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, schema)
                        statement.executeQuery().use { result ->
                            result.next()
                            result.getString(1)
                        }
                    },
            )
        }
    }

    @Test
    fun `V9 enforces exact ingestion enum sets and valid outcome combinations`() {
        val schema = "v9_${UUID.randomUUID().toString().replace("-", "")}"
        migrate(schema)

        connection(schema).use { connection ->
            val policyId = insertPolicy(connection)
            val runId = insertRun(connection, policyId)
            listOf(
                "NO_MATCH" to "DRAFT_CREATED",
                "EXACT_PROVIDER_RECORD" to "DUPLICATE_SKIPPED",
                "EXACT_NAME_ADDRESS" to "MATCHED_EXISTING",
                "EXACT_NAME_COORDINATES" to "MATCHED_EXISTING",
                "AMBIGUOUS" to "QUARANTINED",
                "RESOLVED_EXISTING" to "RESOLVED",
                "RESOLVED_DRAFT" to "RESOLVED",
                "NOT_EVALUATED" to "BLOCKED_BY_GATE",
                "NOT_EVALUATED" to "REJECTED",
                "NO_MATCH" to "ITEM_FAILED",
            ).forEachIndexed { index, (matchStatus, outcome) ->
                insertProvenance(connection, runId, "record-$index", "dedup-$index", matchStatus, outcome)
            }

            expectSqlState("23505") { insertRun(connection, policyId) }
            expectSqlState("23514") { insertRun(connection, policyId, status = "QUEUED", idempotencyKey = "bad-status") }
            expectSqlState("23514") { insertProvenance(connection, runId, "bad-match", "bad-match", "WEAK_MATCH", "DRAFT_CREATED") }
            expectSqlState("23514") { insertProvenance(connection, runId, "bad-outcome", "bad-outcome", "NO_MATCH", "AUTO_PUBLISHED") }
            expectSqlState("23514") { insertProvenance(connection, runId, "bad-combo", "bad-combo", "AMBIGUOUS", "DRAFT_CREATED") }
        }
    }

    @Test
    fun `V9 enforces provider record and fallback dedup identities conditionally`() {
        val schema = "v9_${UUID.randomUUID().toString().replace("-", "")}"
        migrate(schema)

        connection(schema).use { connection ->
            val policyId = insertPolicy(connection)
            val runId = insertRun(connection, policyId)
            insertProvenance(connection, runId, "external-1", "dedup-a", "NO_MATCH", "DRAFT_CREATED")
            expectSqlState("23505") { insertProvenance(connection, runId, "external-1", "dedup-b", "NO_MATCH", "DRAFT_CREATED") }

            insertProvenance(connection, runId, null, "fallback-1", "AMBIGUOUS", "QUARANTINED")
            expectSqlState("23505") { insertProvenance(connection, runId, null, "fallback-1", "NOT_EVALUATED", "REJECTED") }
            insertProvenance(connection, runId, "external-2", "fallback-1", "EXACT_PROVIDER_RECORD", "DUPLICATE_SKIPPED")
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

    private fun tableNames(
        connection: Connection,
        schema: String,
    ): List<String> =
        connection
            .prepareStatement(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ? AND table_type = 'BASE TABLE'
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schema)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString("table_name")) }
                }
            }

    private fun hasColumn(
        connection: Connection,
        schema: String,
        table: String,
        column: String,
    ): Boolean =
        connection
            .prepareStatement(
                """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schema)
                statement.setString(2, table)
                statement.setString(3, column)
                statement.executeQuery().use { it.next() }
            }

    private fun insertPolicy(connection: Connection): UUID =
        UUID.randomUUID().also { id ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO candidate_ingestion_provider_policy (
                        id, provider_key, approval_status, approval_owner, reviewed_at, approved_source_url,
                        allowed_fields, retention_rules, gate_version, sample_precision_result_reference,
                        sample_size, region_count, precision_threshold, active, notes
                    ) VALUES (?, 'semas', 'APPROVED', 'qa-admin', CURRENT_TIMESTAMP, 'https://example.test/approval',
                        'name,address,coordinates,phone,industryCode', 'digest-only', 'gate-v1', 'sample-v1',
                        100, 5, 90.00, TRUE, 'schema test')
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, id)
                    statement.executeUpdate()
                }
        }

    private fun insertRun(
        connection: Connection,
        policyId: UUID,
        status: String = "RUNNING",
        idempotencyKey: String = "same-run-key",
    ): UUID =
        UUID.randomUUID().also { id ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO candidate_ingestion_runs (
                        id, provider_policy_id, provider_key, idempotency_key, requested_by, requested_at, started_at,
                        status, gate_version, seen_count, deduped_count, accepted_count, quarantined_count, rejected_count, failed_count
                    ) VALUES (?, ?, 'semas', ?, 'qa-admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        ?, 'gate-v1', 0, 0, 0, 0, 0, 0)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, id)
                    statement.setObject(2, policyId)
                    statement.setString(3, idempotencyKey)
                    statement.setString(4, status)
                    statement.executeUpdate()
                }
        }

    private fun insertProvenance(
        connection: Connection,
        runId: UUID,
        providerRecordId: String?,
        dedupKey: String,
        matchStatus: String,
        outcome: String,
    ) {
        connection
            .prepareStatement(
                """
                INSERT INTO store_candidate_provenance (
                    id, run_id, provider_key, provider_record_id, dedup_key, first_seen_run_id, last_seen_run_id,
                    first_seen_at, last_seen_at, source_type, source_url, normalized_name, road_address,
                    rounded_latitude, rounded_longitude, phone, industry_code, match_precedence, match_status,
                    match_reason, latest_item_outcome, payload_sha256_digest, created_by, edited_by
                ) VALUES (?, ?, 'semas', ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'API', 'https://example.test/item',
                    'trail shop', 'Seoul road', 37.500000, 127.000000, '02-0000-0000', '47640', 'PROVIDER_RECORD', ?,
                    'schema test', ?, repeat('a', 64), 'qa-admin', 'qa-admin')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, runId)
                statement.setString(3, providerRecordId)
                statement.setString(4, dedupKey)
                statement.setObject(5, runId)
                statement.setObject(6, runId)
                statement.setString(7, matchStatus)
                statement.setString(8, outcome)
                statement.executeUpdate()
            }
    }

    private fun expectSqlState(
        expected: String,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected SQL state $expected")
        } catch (exception: SQLException) {
            assertEquals(expected, exception.sqlState)
        }
    }

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
