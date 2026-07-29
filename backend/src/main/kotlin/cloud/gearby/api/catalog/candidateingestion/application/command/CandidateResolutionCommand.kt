package cloud.gearby.api.catalog.candidateingestion.application.command

import cloud.gearby.api.catalog.application.command.StoreUpsertCommand
import java.util.UUID

sealed interface CandidateResolutionCommand {
    data class LinkExisting(
        val itemId: UUID,
        val storeId: UUID,
        val actor: String,
    ) : CandidateResolutionCommand

    data class CreateDraft(
        val itemId: UUID,
        val store: StoreUpsertCommand,
        val actor: String,
    ) : CandidateResolutionCommand
}
