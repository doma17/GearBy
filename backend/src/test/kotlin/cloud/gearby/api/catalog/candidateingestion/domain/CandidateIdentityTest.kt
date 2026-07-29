package cloud.gearby.api.catalog.candidateingestion.domain

import cloud.gearby.api.catalog.domain.Category
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

@Tag("unit")
class CandidateIdentityTest {
    @Test
    fun `identity precedence is provider id then name address then name coordinates`() {
        assertEquals(
            CandidateMatchPrecedence.PROVIDER_RECORD,
            ExternalStoreCandidate(
                providerRecordId = "  ABC-1 ",
                name = " Trail   Shop ",
                roadAddress = " Seoul Road ",
                latitude = BigDecimal("37.1234567"),
                longitude = BigDecimal("127.1234567"),
                sourceUrl = "https://example.test/1",
                categories = setOf(Category.HIKING),
                payloadSha256Digest = "a".repeat(64),
            ).normalized().identity.precedence,
        )
        assertEquals(
            CandidateMatchPrecedence.NAME_ADDRESS,
            ExternalStoreCandidate(
                providerRecordId = null,
                name = " Trail   Shop ",
                roadAddress = " Seoul Road ",
                sourceUrl = "https://example.test/2",
                categories = setOf(Category.HIKING),
                payloadSha256Digest = "b".repeat(64),
            ).normalized().identity.precedence,
        )
        assertEquals(
            CandidateMatchPrecedence.NAME_COORDINATES,
            ExternalStoreCandidate(
                providerRecordId = null,
                name = " Trail   Shop ",
                roadAddress = null,
                latitude = BigDecimal("37.1234567"),
                longitude = BigDecimal("127.1234567"),
                sourceUrl = "https://example.test/3",
                categories = setOf(Category.HIKING),
                payloadSha256Digest = "c".repeat(64),
            ).normalized().identity.precedence,
        )
    }

    @Test
    fun `normalization is deterministic and coordinates round to six decimals`() {
        val candidate =
            ExternalStoreCandidate(
                name = "  Trail\tShop  ",
                roadAddress = "  Seoul   Road ",
                latitude = BigDecimal("37.1234567"),
                longitude = BigDecimal("127.1234564"),
                sourceUrl = "https://example.test/4",
                categories = setOf(Category.CAMPING),
                payloadSha256Digest = "d".repeat(64),
            ).normalized()

        assertEquals("trail shop", candidate.normalizedName)
        assertEquals("seoul road", candidate.roadAddress)
        assertEquals(BigDecimal("37.123457"), candidate.roundedLatitude)
        assertEquals(BigDecimal("127.123456"), candidate.roundedLongitude)
        assertEquals("name-address:trail shop|seoul road", candidate.identity.dedupKey)
    }

    @Test
    fun `weaker than name address or name coordinates is ambiguous`() {
        val candidate =
            ExternalStoreCandidate(
                name = "Trail Shop",
                sourceUrl = "https://example.test/5",
                categories = setOf(Category.HIKING),
                payloadSha256Digest = "e".repeat(64),
            ).normalized()

        assertEquals(CandidateMatchPrecedence.AMBIGUOUS, candidate.identity.precedence)
    }
}
