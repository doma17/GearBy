package cloud.gearby.api.catalog.candidateingestion.operator

import cloud.gearby.api.catalog.candidateingestion.application.command.ProviderIngestionCommand
import cloud.gearby.api.catalog.candidateingestion.application.port.StoreCandidateProvider
import cloud.gearby.api.catalog.candidateingestion.application.service.CandidateIngestionService
import cloud.gearby.api.catalog.candidateingestion.infrastructure.semas.SemasIngestionProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("candidate-ingestion")
class CandidateIngestionRunner(
    private val properties: CandidateIngestionProperties,
    private val semasProperties: SemasIngestionProperties,
    private val providers: ObjectProvider<StoreCandidateProvider>,
    private val ingestion: CandidateIngestionService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return
        properties.validate(semasProperties.serviceKey)
        ingestion.ingestFromProvider(
            ProviderIngestionCommand(
                providerKey = properties.provider,
                idempotencyKey = properties.runKey,
                requestedBy = properties.requestedBy,
                allowlistVersion = properties.allowlistVersion,
                industryCodes = properties.industryCodes,
                pageSize = properties.pageSize,
                maxPages = properties.maxPages,
            ),
            providers.getObject(),
        )
    }
}
