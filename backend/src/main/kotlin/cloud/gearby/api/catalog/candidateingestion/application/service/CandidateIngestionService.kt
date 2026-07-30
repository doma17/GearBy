package cloud.gearby.api.catalog.candidateingestion.application.service

import cloud.gearby.api.catalog.candidateingestion.application.command.CandidateIngestionCommand
import cloud.gearby.api.catalog.candidateingestion.application.command.ProviderIngestionCommand
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderCandidatePage
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailure
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailureCategory
import cloud.gearby.api.catalog.candidateingestion.application.port.StoreCandidateProvider
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateIngestionResult
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateItemResult
import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.catalog.candidateingestion.infrastructure.implement.CandidateIngestionManager
import cloud.gearby.api.catalog.candidateingestion.infrastructure.implement.CandidateIngestionReader
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.sql.SQLException
import java.util.UUID

@Service
class CandidateIngestionService(
    private val reader: CandidateIngestionReader,
    private val manager: CandidateIngestionManager,
    private val transactions: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ingest(command: CandidateIngestionCommand): CandidateIngestionResult {
        reader.runByKey(command.providerKey, command.idempotencyKey)?.let { return it.toResult(idempotent = true) }
        val started = startRun(command.providerKey, command.idempotencyKey, command.requestedBy)
        if (!started.created) return started.run.toResult(idempotent = true)
        val run = started.run
        command.candidates.forEach { candidate ->
            transactions.execute { manager.recordCandidate(run.id, candidate.normalized(), command.requestedBy) }
        }
        return transactions.execute { manager.finishRun(run.id).toResult(idempotent = false) }
    }

    fun ingestFromProvider(
        command: ProviderIngestionCommand,
        provider: StoreCandidateProvider,
    ): CandidateIngestionResult {
        validateKey(command)
        transactions
            .execute {
                reader.runByKey(command.providerKey, command.idempotencyKey)?.let { existing ->
                    return@execute if (existing.status == IngestionRunStatus.RUNNING) {
                        manager.interruptRun(existing.id).toResult(idempotent = true)
                    } else {
                        existing.toResult(idempotent = true)
                    }
                }
                null
            }?.let { return it }
        validateMutableConfig(command)

        val started = startRun(command.providerKey, command.idempotencyKey, command.requestedBy, command.allowlistVersion)
        if (!started.created) return started.run.toResult(idempotent = true)
        val run = started.run
        log.info("candidate_ingestion_start runId={} status={}", run.id, run.status)
        try {
            command.industryCodes.forEach { code ->
                val pageSignatures = mutableSetOf<String>()
                var pageNo = 1
                while (pageNo <= command.maxPages) {
                    val page = provider.fetchPage(code, pageNo, command.pageSize)
                    if (!pageSignatures.add(page.signature())) {
                        throw ProviderFailure(ProviderFailureCategory.REPEATED_PAGE, "provider page repeated")
                    }
                    page.candidates.forEach { candidate ->
                        transactions.execute { manager.recordCandidate(run.id, candidate.normalized(), command.requestedBy) }
                    }
                    if (!page.hasNext) break
                    if (pageNo == command.maxPages) {
                        throw ProviderFailure(ProviderFailureCategory.PAGE_LIMIT, "provider page limit reached")
                    }
                    pageNo += 1
                }
            }
            val finished = transactions.execute { manager.finishRun(run.id).toResult(idempotent = false) }
            logResult("candidate_ingestion_finish", finished)
            return finished
        } catch (failure: ProviderFailure) {
            val failed =
                transactions.execute {
                    manager
                        .failRun(
                            run.id,
                            failure.category.name,
                            failure.safeSummary(),
                        ).toResult(idempotent = false)
                }
            logResult("candidate_ingestion_finish", failed)
            return failed
        }
    }

    fun items(runId: UUID): List<CandidateItemResult> =
        reader.provenanceForRun(runId).map { CandidateItemResult(it.id, it.latestItemOutcome, it.matchStatus, it.resolvedStoreId) }

    private fun startRun(
        providerKey: String,
        idempotencyKey: String,
        requestedBy: String,
        allowlistVersion: String? = null,
    ): StartedRun {
        reader.runByKey(providerKey, idempotencyKey)?.let { return StartedRun(it, created = false) }
        try {
            val run =
                requireNotNull(
                    transactions.execute {
                        manager.startRun(providerKey, idempotencyKey, requestedBy, allowlistVersion)
                    },
                )
            return StartedRun(run, created = true)
        } catch (error: DataIntegrityViolationException) {
            if (!error.isIdempotencyConflict()) throw error
            val existing = reader.runByKey(providerKey, idempotencyKey) ?: throw error
            return StartedRun(existing, created = false)
        }
    }

    private fun DataIntegrityViolationException.isIdempotencyConflict(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any { cause ->
            cause is SQLException &&
                cause.sqlState == "23505" &&
                cause.message.orEmpty().contains(IDEMPOTENCY_CONSTRAINT)
        }

    private data class StartedRun(
        val run: CandidateIngestionRunEntity,
        val created: Boolean,
    )

    private fun validateKey(command: ProviderIngestionCommand) {
        require(command.providerKey == "semas") { "provider must be semas" }
        require(command.idempotencyKey.isNotBlank()) { "run key is required" }
    }

    private fun validateMutableConfig(command: ProviderIngestionCommand) {
        require(command.allowlistVersion.isNotBlank()) { "allowlist version is required" }
        require(command.industryCodes.isNotEmpty()) { "industry codes are required" }
        require(command.industryCodes.all { it.matches(Regex("^\\d{6}$")) }) { "industry codes must be 6 digits" }
        require(command.pageSize in 1..1000) { "page size must be between 1 and 1000" }
        require(command.maxPages in 1..1000) { "max pages must be between 1 and 1000" }
    }

    private fun ProviderCandidatePage.signature(): String {
        val value =
            candidates.joinToString(
                "|",
            ) { "${it.providerRecordId.orEmpty()}:${it.name}:${it.roadAddress.orEmpty()}" }
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun ProviderFailure.safeSummary(): String =
        when (category) {
            ProviderFailureCategory.AUTH -> "provider authentication failed"
            ProviderFailureCategory.QUOTA -> "provider quota exceeded"
            ProviderFailureCategory.MALFORMED_RESPONSE -> "provider response was malformed"
            ProviderFailureCategory.REPEATED_PAGE -> "provider pagination repeated"
            ProviderFailureCategory.NETWORK -> "provider network failure"
            ProviderFailureCategory.CONFIGURATION -> "provider configuration failed"
            ProviderFailureCategory.PAGE_LIMIT -> "provider page limit reached"
        }

    private companion object {
        const val IDEMPOTENCY_CONSTRAINT = "candidate_ingestion_runs_provider_key_idempotency_key_key"
    }

    private fun logResult(
        event: String,
        result: CandidateIngestionResult,
    ) {
        log.info(
            "{} runId={} status={} seen={} accepted={} deduped={} quarantined={} rejected={} failed={}",
            event,
            result.runId,
            result.status,
            result.seenCount,
            result.acceptedCount,
            result.dedupedCount,
            result.quarantinedCount,
            result.rejectedCount,
            result.failedCount,
        )
    }
}
