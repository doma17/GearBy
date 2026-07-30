package cloud.gearby.api.catalog.candidateingestion.infrastructure.implement

import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchPrecedence
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateProviderPolicy
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateSourceType
import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.catalog.candidateingestion.domain.NormalizedStoreCandidate
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionProviderPolicyEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.CandidateIngestionRunEntity
import cloud.gearby.api.catalog.candidateingestion.infrastructure.persistence.entity.StoreCandidateProvenanceEntity
import cloud.gearby.api.catalog.domain.Coordinates
import cloud.gearby.api.catalog.infrastructure.implement.CatalogManager
import cloud.gearby.api.catalog.infrastructure.implement.CatalogReader
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

private val spaces = Regex("\\s+")
private val digest = Regex("^[0-9a-f]{64}$")

@Component
class CandidateIngestionManager(
    private val reader: CandidateIngestionReader,
    private val appender: CandidateIngestionAppender,
    private val catalogReader: CatalogReader,
    private val catalogManager: CatalogManager,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun startRun(
        providerKey: String,
        idempotencyKey: String,
        actor: String,
        requiredGateVersion: String? = null,
    ): CandidateIngestionRunEntity {
        reader.runByKey(providerKey, idempotencyKey)?.let { return it }
        val policy = requireNotNull(reader.activePolicy(providerKey)) { "active provider policy is required" }
        require(policy.toPolicy().allowsSemasIngestion()) { "provider policy is not approved" }
        require(requiredGateVersion == null || requiredGateVersion == policy.gateVersion) { "allowlist version is not approved" }
        return appender.createRun(
            CandidateIngestionRunEntity(
                providerPolicyId = policy.id,
                providerKey = providerKey,
                idempotencyKey = idempotencyKey,
                requestedBy = actor,
                requestedAt = clock.instant(),
                startedAt = clock.instant(),
                status = IngestionRunStatus.RUNNING,
                gateVersion = policy.gateVersion,
            ).apply { createdBy(actor) },
        )
    }

    fun recordCandidate(
        runId: UUID,
        candidate: NormalizedStoreCandidate,
        actor: String,
    ): StoreCandidateProvenanceEntity {
        val run = requireNotNull(reader.run(runId)) { "run not found" }
        require(run.status == IngestionRunStatus.RUNNING) { "run must be running" }
        validate(candidate)
        run.seenCount += 1
        val item = classify(run, candidate, actor)
        count(run, item.latestItemOutcome)
        appender.saveRun(run)
        return item
    }

    fun resolveWithExistingStore(
        itemId: UUID,
        storeId: UUID,
        actor: String,
    ): StoreCandidateProvenanceEntity {
        val item = requireNotNull(reader.provenance(itemId)) { "candidate item not found" }
        val store = requireNotNull(catalogReader.findStore(storeId)) { "store not found" }
        if (item.latestItemOutcome == CandidateItemOutcome.RESOLVED) {
            if (item.matchStatus == CandidateMatchStatus.RESOLVED_EXISTING && item.resolvedStoreId == storeId) return item
            throw IllegalStateException("candidate item is already resolved")
        }
        require(item.matchStatus == CandidateMatchStatus.AMBIGUOUS && item.latestItemOutcome == CandidateItemOutcome.QUARANTINED) {
            "candidate item is not resolvable"
        }
        item.matchStatus = CandidateMatchStatus.RESOLVED_EXISTING
        item.latestItemOutcome = CandidateItemOutcome.RESOLVED
        item.resolvedStoreId = store.id
        item.touch(actor)
        return appender.saveProvenance(item)
    }

    fun resolveWithDraftStore(
        itemId: UUID,
        command: StoreUpsertCommand,
        actor: String,
    ): StoreCandidateProvenanceEntity {
        val item = requireNotNull(reader.provenance(itemId)) { "candidate item not found" }
        if (item.latestItemOutcome == CandidateItemOutcome.RESOLVED) throw IllegalStateException("candidate item is already resolved")
        require(item.matchStatus == CandidateMatchStatus.AMBIGUOUS && item.latestItemOutcome == CandidateItemOutcome.QUARANTINED) {
            "candidate item is not resolvable"
        }
        val store = catalogManager.createStore(command, actor)
        item.matchStatus = CandidateMatchStatus.RESOLVED_DRAFT
        item.latestItemOutcome = CandidateItemOutcome.RESOLVED
        item.resolvedStoreId = store.id
        item.touch(actor)
        return appender.saveProvenance(item)
    }

    fun interruptRun(runId: UUID): CandidateIngestionRunEntity {
        val run = requireNotNull(reader.run(runId)) { "run not found" }
        if (run.status == IngestionRunStatus.RUNNING) {
            run.status = if (run.seenCount > 0) IngestionRunStatus.PARTIAL else IngestionRunStatus.FAILED
            run.errorCode = "INTERRUPTED"
            run.errorSummary = "stale running run interrupted; retry with a new key"
            run.finishedAt = clock.instant()
            appender.saveRun(run)
        }
        return run
    }

    fun failRun(
        runId: UUID,
        errorCode: String,
        errorSummary: String,
    ): CandidateIngestionRunEntity {
        val run = requireNotNull(reader.run(runId)) { "run not found" }
        if (run.status == IngestionRunStatus.RUNNING) {
            run.status = if (run.seenCount > 0) IngestionRunStatus.PARTIAL else IngestionRunStatus.FAILED
            run.errorCode = errorCode
            run.errorSummary = errorSummary
            run.finishedAt = clock.instant()
            appender.saveRun(run)
        }
        return run
    }

    fun finishRun(runId: UUID): CandidateIngestionRunEntity {
        val run = requireNotNull(reader.run(runId)) { "run not found" }
        if (run.status == IngestionRunStatus.RUNNING) {
            run.status = if (run.failedCount > 0) IngestionRunStatus.PARTIAL else IngestionRunStatus.COMPLETED
            run.finishedAt = clock.instant()
            appender.saveRun(run)
        }
        return run
    }

    private fun classify(
        run: CandidateIngestionRunEntity,
        candidate: NormalizedStoreCandidate,
        actor: String,
    ): StoreCandidateProvenanceEntity {
        candidate.providerRecordId?.let { providerRecordId ->
            reader.provenanceByProviderRecord(run.providerKey, providerRecordId)?.let {
                return updateSeen(it, run.id, CandidateMatchStatus.EXACT_PROVIDER_RECORD, CandidateItemOutcome.DUPLICATE_SKIPPED, actor)
            }
        }
        if (candidate.providerRecordId == null) {
            reader.provenanceByDedupKey(run.providerKey, candidate.identity.dedupKey)?.let {
                val (status, outcome) =
                    when (candidate.identity.precedence) {
                        CandidateMatchPrecedence.NAME_ADDRESS ->
                            CandidateMatchStatus.EXACT_NAME_ADDRESS to CandidateItemOutcome.MATCHED_EXISTING
                        CandidateMatchPrecedence.NAME_COORDINATES ->
                            CandidateMatchStatus.EXACT_NAME_COORDINATES to CandidateItemOutcome.MATCHED_EXISTING
                        else -> CandidateMatchStatus.AMBIGUOUS to CandidateItemOutcome.QUARANTINED
                    }
                return updateSeen(it, run.id, status, outcome, actor)
            }
        }
        if (candidate.identity.precedence == CandidateMatchPrecedence.AMBIGUOUS || candidate.categories.isEmpty()) {
            return createProvenance(run, candidate, CandidateMatchStatus.AMBIGUOUS, CandidateItemOutcome.QUARANTINED, actor)
        }
        val storeMatches = matchingStores(candidate)
        if (storeMatches.size == 1) {
            val (storeId, status) = storeMatches.single()
            return createProvenance(run, candidate, status, CandidateItemOutcome.MATCHED_EXISTING, actor, storeId)
        }
        if (storeMatches.size > 1) {
            return createProvenance(run, candidate, CandidateMatchStatus.AMBIGUOUS, CandidateItemOutcome.QUARANTINED, actor)
        }
        val store =
            catalogManager.createStore(
                StoreUpsertCommand(
                    name = candidate.normalizedName,
                    address = candidate.roadAddress ?: "",
                    coordinates = Coordinates(requireNotNull(candidate.roundedLatitude), requireNotNull(candidate.roundedLongitude)),
                    categories = candidate.categories,
                    phone = candidate.phone,
                ),
                actor,
            )
        return createProvenance(run, candidate, CandidateMatchStatus.NO_MATCH, CandidateItemOutcome.DRAFT_CREATED, actor, store.id)
    }

    private fun matchingStores(candidate: NormalizedStoreCandidate): List<Pair<UUID, CandidateMatchStatus>> =
        catalogReader
            .allStores()
            .mapNotNull { store ->
                val sameName = normalize(store.name) == candidate.normalizedName
                val sameAddress = candidate.roadAddress != null && normalize(store.address) == candidate.roadAddress
                val sameCoordinates =
                    candidate.roundedLatitude != null &&
                        candidate.roundedLongitude != null &&
                        store.latitude.setScale(6) == candidate.roundedLatitude &&
                        store.longitude.setScale(6) == candidate.roundedLongitude
                when {
                    sameName && sameAddress -> store.id to CandidateMatchStatus.EXACT_NAME_ADDRESS
                    sameName && sameCoordinates -> store.id to CandidateMatchStatus.EXACT_NAME_COORDINATES
                    else -> null
                }
            }

    private fun createProvenance(
        run: CandidateIngestionRunEntity,
        candidate: NormalizedStoreCandidate,
        status: CandidateMatchStatus,
        outcome: CandidateItemOutcome,
        actor: String,
        resolvedStoreId: UUID? = null,
    ): StoreCandidateProvenanceEntity {
        val now = clock.instant()
        return appender.saveProvenance(
            StoreCandidateProvenanceEntity(
                runId = run.id,
                providerKey = run.providerKey,
                providerRecordId = candidate.providerRecordId,
                dedupKey = candidate.identity.dedupKey,
                firstSeenRunId = run.id,
                lastSeenRunId = run.id,
                firstSeenAt = now,
                lastSeenAt = now,
                sourceType = CandidateSourceType.API,
                sourceUrl = candidate.sourceUrl,
                normalizedName = candidate.normalizedName,
                roadAddress = candidate.roadAddress,
                roundedLatitude = candidate.roundedLatitude,
                roundedLongitude = candidate.roundedLongitude,
                phone = candidate.phone,
                industryCode = candidate.industryCode,
                matchPrecedence = candidate.identity.precedence,
                matchStatus = status,
                latestItemOutcome = outcome,
                resolvedStoreId = resolvedStoreId,
                payloadSha256Digest = candidate.payloadSha256Digest,
            ).apply { createdBy(actor) },
        )
    }

    private fun updateSeen(
        item: StoreCandidateProvenanceEntity,
        runId: UUID,
        status: CandidateMatchStatus,
        outcome: CandidateItemOutcome,
        actor: String,
    ): StoreCandidateProvenanceEntity {
        item.lastSeenRunId = runId
        item.lastSeenAt = clock.instant()
        item.matchStatus = status
        item.latestItemOutcome = outcome
        item.touch(actor)
        return appender.saveProvenance(item)
    }

    private fun validate(candidate: NormalizedStoreCandidate) {
        require(candidate.normalizedName.isNotBlank()) { "name is required" }
        require(candidate.sourceUrl.isNotBlank()) { "sourceUrl is required" }
        require(digest.matches(candidate.payloadSha256Digest)) { "payload digest must be sha-256 hex" }
        if (candidate.identity.precedence != CandidateMatchPrecedence.AMBIGUOUS) {
            require(
                candidate.roundedLatitude != null && candidate.roundedLongitude != null,
            ) { "coordinates are required for accepted candidates" }
        }
    }

    private fun count(
        run: CandidateIngestionRunEntity,
        outcome: CandidateItemOutcome,
    ) {
        when (outcome) {
            CandidateItemOutcome.DRAFT_CREATED -> run.acceptedCount += 1
            CandidateItemOutcome.MATCHED_EXISTING, CandidateItemOutcome.DUPLICATE_SKIPPED -> run.dedupedCount += 1
            CandidateItemOutcome.QUARANTINED -> run.quarantinedCount += 1
            CandidateItemOutcome.BLOCKED_BY_GATE, CandidateItemOutcome.REJECTED -> run.rejectedCount += 1
            CandidateItemOutcome.ITEM_FAILED -> run.failedCount += 1
            CandidateItemOutcome.RESOLVED -> Unit
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(spaces, " ")

    private fun CandidateIngestionProviderPolicyEntity.toPolicy() =
        CandidateProviderPolicy(
            providerKey,
            approvalStatus,
            reviewedAt,
            approvedSourceUrl,
            gateVersion,
            sampleSize,
            regionCount,
            precisionThreshold,
            active,
        )
}
