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
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
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
    fun `category metadata uses Korean labels`() {
        assertEquals(
            mapOf("HIKING" to "등산", "BACKPACKING" to "백패킹", "CAMPING" to "캠핑", "CLIMBING" to "클라이밍"),
            Category.entries.associate { it.name to it.displayName },
        )
    }

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
    fun `search matches a category display name`() {
        val backpackingStore = store("Pack House", setOf(Category.BACKPACKING))
        `when`(reader.storesByStatus(StoreStatus.PUBLISHED)).thenReturn(listOf(backpackingStore))

        val result = catalog.search(StoreQuery(query = "백패킹"))

        assertEquals(listOf(backpackingStore.id), result.items.map { it.id })
        verify(reader, never()).correctionFor("백패킹")
    }

    @Test
    fun `search skips correction lookup when correction application is disabled`() {
        `when`(reader.storesByStatus(StoreStatus.PUBLISHED)).thenReturn(listOf(store("Trail House", setOf(Category.HIKING))))

        val result = catalog.search(StoreQuery(query = "backpakin", applyCorrection = false))

        assertEquals(emptyList(), result.items)
        assertEquals("backpakin", result.search?.appliedQuery)
        assertEquals(null, result.search?.correction)
        verify(reader, never()).correctionFor("backpakin")
    }

    @Test
    fun `search preserves a direct original match before correction lookup`() {
        val direct = store("Backpakin House", setOf(Category.HIKING))
        `when`(reader.storesByStatus(StoreStatus.PUBLISHED)).thenReturn(listOf(direct))

        val result = catalog.search(StoreQuery(query = "backpakin"))

        assertEquals(listOf(direct.id), result.items.map { it.id })
        assertEquals("backpakin", result.search?.appliedQuery)
        assertEquals(null, result.search?.correction)
        verify(reader, never()).correctionFor("backpakin")
    }

    @Test
    fun `search query defaults correction application to true`() {
        assertEquals(true, StoreQuery().applyCorrection)
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
