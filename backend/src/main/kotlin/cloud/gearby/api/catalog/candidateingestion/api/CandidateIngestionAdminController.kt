package cloud.gearby.api.catalog.candidateingestion.api

import cloud.gearby.api.catalog.candidateingestion.api.request.CandidateResolutionRequest
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidateItemResponse
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidatePageResponse
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidateResolutionResponse
import cloud.gearby.api.catalog.candidateingestion.api.response.CandidateRunResponse
import cloud.gearby.api.catalog.candidateingestion.application.query.CandidateItemListQuery
import cloud.gearby.api.catalog.candidateingestion.application.query.CandidateRunListQuery
import cloud.gearby.api.catalog.candidateingestion.application.service.CandidateIngestionAdminService
import cloud.gearby.api.catalog.candidateingestion.application.service.CandidateNotFound
import cloud.gearby.api.catalog.candidateingestion.application.service.CandidateResolutionConflict
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateItemOutcome
import cloud.gearby.api.catalog.candidateingestion.domain.CandidateMatchStatus
import cloud.gearby.api.catalog.candidateingestion.domain.IngestionRunStatus
import cloud.gearby.api.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/candidate-ingestion")
class CandidateIngestionAdminController(
    private val admin: CandidateIngestionAdminService,
) {
    @GetMapping("/runs")
    fun runs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) provider: String?,
    ): ApiResponse<CandidatePageResponse<CandidateRunResponse>> =
        ApiResponse.success(
            admin
                .runs(CandidateRunListQuery(page, size, status?.toEnum<IngestionRunStatus>("status"), provider?.takeIf(String::isNotBlank)))
                .toResponse { it.toResponse() },
        )

    @GetMapping("/runs/{runId}")
    fun run(
        @PathVariable runId: UUID,
    ): ApiResponse<CandidateRunResponse> =
        ApiResponse.success(
            admin.run(runId)?.toResponse() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "run not found"),
        )

    @GetMapping("/items")
    fun items(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) runId: UUID?,
        @RequestParam(required = false) latestOutcome: String?,
        @RequestParam(required = false) latestMatchStatus: String?,
    ): ApiResponse<CandidatePageResponse<CandidateItemResponse>> =
        ApiResponse.success(
            admin
                .items(
                    CandidateItemListQuery(
                        page,
                        size,
                        runId,
                        latestOutcome?.toEnum<CandidateItemOutcome>("latestOutcome"),
                        latestMatchStatus?.toEnum<CandidateMatchStatus>("latestMatchStatus"),
                    ),
                ).toResponse { it.toResponse() },
        )

    @PostMapping("/items/{itemId}/resolve")
    fun resolve(
        @PathVariable itemId: UUID,
        @RequestBody request: CandidateResolutionRequest,
        principal: Principal,
    ): ApiResponse<CandidateResolutionResponse> =
        try {
            ApiResponse.success(admin.resolve(request.toCommand(itemId, principal)).toResponse())
        } catch (error: CandidateResolutionConflict) {
            throw ResponseStatusException(HttpStatus.CONFLICT, error.message)
        } catch (error: CandidateNotFound) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, error.message)
        }

    private inline fun <reified T : Enum<T>> String.toEnum(parameter: String): T =
        try {
            enumValueOf<T>(trim())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("invalid $parameter")
        }
}
