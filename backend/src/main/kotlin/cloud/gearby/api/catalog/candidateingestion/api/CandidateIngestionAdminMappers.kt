package cloud.gearby.api.catalog.candidateingestion.api

import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import cloud.gearby.api.catalog.candidateingestion.api.request.CandidateResolutionRequest
import cloud.gearby.api.catalog.candidateingestion.api.request.CandidateResolutionType
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidateItemResponse
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidatePageResponse
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidateResolutionResponse
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidateRunResponse
import cloud.gearby.api.catalog.candidateingestion.application.command.CandidateResolutionCommand
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateItemAdminResult
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateResolutionResult
import cloud.gearby.api.catalog.candidateingestion.application.result.CandidateRunAdminResult
import cloud.gearby.api.catalog.candidateingestion.application.result.PageResult
import cloud.gearby.api.catalog.domain.Coordinates
import java.security.Principal
import java.util.UUID

fun CandidateRunAdminResult.toResponse() =
    CandidateRunResponse(
        id,
        provider,
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

fun CandidateItemAdminResult.toResponse() =
    CandidateItemResponse(
        id,
        firstSeenRunId,
        lastSeenRunId,
        provider,
        providerRecordId,
        sourceUrl,
        normalizedName,
        roadAddress,
        roundedLatitude,
        roundedLongitude,
        phone,
        industryCode,
        latestOutcome,
        latestMatchStatus,
        resolvedStoreId,
        resolvedStoreStatus,
        createdAt,
        updatedAt,
    )

fun CandidateResolutionResult.toResponse() = CandidateResolutionResponse(itemId, outcome, matchStatus, resolvedStoreId, resolvedStoreStatus)

fun <A, B> PageResult<A>.toResponse(mapper: (A) -> B) = CandidatePageResponse(items.map(mapper), page, size, total)

fun CandidateResolutionRequest.toCommand(
    itemId: UUID,
    principal: Principal,
): CandidateResolutionCommand =
    when (resolutionType) {
        CandidateResolutionType.LINK_EXISTING ->
            CandidateResolutionCommand.LinkExisting(
                itemId,
                requireNotNull(storeId) {
                    "storeId is required"
                },
                principal.name,
            )
        CandidateResolutionType.CREATE_DRAFT ->
            CandidateResolutionCommand.CreateDraft(
                itemId,
                StoreUpsertCommand(
                    requireNotNull(name) { "name is required" },
                    requireNotNull(address) { "address is required" },
                    requireNotNull(coordinates) { "coordinates are required" }.let { Coordinates(it.latitude, it.longitude) },
                    requireNotNull(categories) { "categories are required" },
                    phone,
                    hours,
                    description,
                ),
                principal.name,
            )
    }
