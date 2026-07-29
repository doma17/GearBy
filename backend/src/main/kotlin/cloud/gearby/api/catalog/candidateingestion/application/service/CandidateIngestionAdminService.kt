package cloud.gearby.api.catalog.candidateingestion.application.service

import cloud.gearby.api.catalog.candidateingestion.application.command.CandidateResolutionCommand
import cloud.gearby.api.catalog.candidateingestion.application.query.CandidateItemListQuery
import cloud.gearby.api.catalog.candidateingestion.application.query.CandidateRunListQuery
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateItemAdminResult
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateResolutionResult
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateRunAdminResult
import cloud.gearby.api.catalog.candidateingestion.application.result.PageResult
import cloud.gearby.api.catalog.candidateingestion.infrastructure.implement.CandidateIngestionManager
import cloud.gearby.api.catalog.candidateingestion.infrastructure.implement.CandidateIngestionReader
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.StoreCandidateProvenanceEntity
import cloud.gearby.api.catalog.infrastructure.implement.CatalogReader
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CandidateIngestionAdminService(
    private val reader: CandidateIngestionReader,
    private val manager: CandidateIngestionManager,
    private val catalogReader: CatalogReader,
) {
    fun runs(query: CandidateRunListQuery): PageResult<CandidateRunAdminResult> {
        validatePage(query.page, query.size)
        val pageable = PageRequest.of(query.page, query.size, Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.asc("id")))
        return reader.runs(query.status, query.provider?.trim()?.takeIf(String::isNotBlank), pageable).toResult { it.toAdminResult() }
    }

    fun run(id: UUID): CandidateRunAdminResult? = reader.run(id)?.toAdminResult()

    fun items(query: CandidateItemListQuery): PageResult<CandidateItemAdminResult> {
        validatePage(query.page, query.size)
        val pageable = PageRequest.of(query.page, query.size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
        return reader.provenance(query.runId, query.latestOutcome, query.latestMatchStatus, pageable).toResult { it.toAdminResult() }
    }

    @Transactional
    fun resolve(command: CandidateResolutionCommand): CandidateResolutionResult {
        val itemId =
            when (command) {
                is CandidateResolutionCommand.LinkExisting -> command.itemId
                is CandidateResolutionCommand.CreateDraft -> command.itemId
            }
        if (reader.provenance(itemId) == null) throw CandidateNotFound("candidate item not found")
        if (command is CandidateResolutionCommand.LinkExisting && catalogReader.findStore(command.storeId) == null) {
            throw CandidateNotFound("store not found")
        }
        val item =
            try {
                when (command) {
                    is CandidateResolutionCommand.LinkExisting ->
                        manager.resolveWithExistingStore(
                            command.itemId,
                            command.storeId,
                            command.actor,
                        )
                    is CandidateResolutionCommand.CreateDraft -> manager.resolveWithDraftStore(command.itemId, command.store, command.actor)
                }
            } catch (ex: IllegalStateException) {
                throw CandidateResolutionConflict(ex.message ?: "candidate item is not resolvable")
            }
        val storeId = requireNotNull(item.resolvedStoreId) { "resolved store is required" }
        val status = requireNotNull(catalogReader.findStore(storeId)?.status) { "resolved store not found" }
        return CandidateResolutionResult(item.id, item.latestItemOutcome, item.matchStatus, storeId, status)
    }

    private fun CandidateIngestionRunEntity.toAdminResult() =
        CandidateRunAdminResult(
            id,
            providerKey,
            idempotencyKey,
            requestedBy,
            requestedAt,
            startedAt,
            finishedAt,
            status,
            gateVersion,
            seenCount,
            acceptedCount,
            dedupedCount,
            quarantinedCount,
            rejectedCount,
            failedCount,
            errorCode,
            errorSummary,
        )

    private fun StoreCandidateProvenanceEntity.toAdminResult() =
        CandidateItemAdminResult(
            id,
            firstSeenRunId,
            lastSeenRunId,
            providerKey,
            providerRecordId,
            sourceUrl,
            normalizedName,
            roadAddress,
            roundedLatitude,
            roundedLongitude,
            phone,
            industryCode,
            latestItemOutcome,
            matchStatus,
            resolvedStoreId,
            resolvedStoreId?.let { catalogReader.findStore(it)?.status },
            createdAt,
            updatedAt,
        )

    private fun validatePage(
        page: Int,
        size: Int,
    ) {
        require(page >= 0) { "page must be zero or greater" }
        require(size in 1..100) { "size must be between 1 and 100" }
    }

    private fun <A : Any, B> Page<A>.toResult(mapper: (A) -> B) = PageResult(content.map(mapper), number, size, totalElements)
}
