package cloud.gearby.api.catalog.application.service

import cloud.gearby.api.catalog.application.query.StoreQuery
import cloud.gearby.api.catalog.domain.Category
import cloud.gearby.api.catalog.domain.CorrectionTargetType
import cloud.gearby.api.catalog.domain.StoreStatus
import cloud.gearby.api.catalog.infrastructure.implement.CatalogManager
import cloud.gearby.api.catalog.infrastructure.implement.CatalogReader
import cloud.gearby.api.catalog.infrastructure.persistence.entity.CorrectionRuleEntity
import cloud.gearby.api.catalog.infrastructure.persistence.entity.StoreEntity
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Tag("unit")
class CatalogServiceTest {
    private val reader = mock(CatalogReader::class.java)
    private val manager = mock(CatalogManager::class.java)
    private val catalog = CatalogService(reader, manager)

    @Test
    fun `search applies a category correction when direct matching finds nothing`() {
        val hikingStore = store("Trail House", setOf(Category.HIKING))
        val backpackingStore = store("Pack House", setOf(Category.BACKPACKING))
        `when`(reader.storesByStatus(StoreStatus.PUBLISHED)).thenReturn(listOf(hikingStore, backpackingStore))
        `when`(reader.correctionFor("backpakin")).thenReturn(
            CorrectionRuleEntity(source = "backpakin", targetType = CorrectionTargetType.CATEGORY, target = "BACKPACKING"),
        )

        val result = catalog.search(StoreQuery(query = " backpakin "))

        assertEquals(listOf(backpackingStore.id), result.items.map { it.id })
        assertEquals("backpakin", result.search?.originalQuery)
        assertEquals("BACKPACKING", result.search?.appliedQuery)
        assertEquals("backpakin → BACKPACKING", result.search?.correction)
    }

    @Test
    fun `distance sorting requires coordinates`() {
        assertFailsWith<IllegalArgumentException> {
            catalog.search(StoreQuery(sort = "distance"))
        }
    }

    private fun store(
        name: String,
        categories: Set<Category>,
    ) = StoreEntity(
        id = UUID.randomUUID(),
        name = name,
        address = "Seoul",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
        status = StoreStatus.PUBLISHED,
    ).apply { this.categories.addAll(categories) }
}
