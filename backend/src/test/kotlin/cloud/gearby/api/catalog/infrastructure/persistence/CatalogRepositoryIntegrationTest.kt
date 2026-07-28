package cloud.gearby.api.catalog.infrastructure.persistence

import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.catalog.infrastructure.persistence.repository.CorrectionRuleJpaRepository
import cloud.gearby.api.catalog.infrastructure.persistence.repository.StoreJpaRepository
import cloud.gearby.api.support.PostgresIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@Tag("integration")
@SpringBootTest
class CatalogRepositoryIntegrationTest @Autowired constructor(
    private val stores: StoreJpaRepository,
    private val rules: CorrectionRuleJpaRepository,
) : PostgresIntegrationTest() {
    @Test
    fun `repositories expose Flyway seed data through their query contracts`() {
        assertEquals(
            listOf("GearBy Gyeonggi Camp", "GearBy Seoul Trail"),
            stores.findByStatusOrderByNameAscIdAsc(StoreStatus.PUBLISHED).map { it.name },
        )
        assertNotNull(rules.findBySourceAndActiveTrue("백패킨"))
    }
}
