package cloud.gearby.api

import cloud.gearby.api.catalog.candidateingestion.infrastructure.semas.SemasIngestionProperties
import cloud.gearby.api.catalog.candidateingestion.operator.CandidateIngestionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(CandidateIngestionProperties::class, SemasIngestionProperties::class)
class GearbyApplication

fun main(args: Array<String>) {
    runApplication<GearbyApplication>(*args)
}
