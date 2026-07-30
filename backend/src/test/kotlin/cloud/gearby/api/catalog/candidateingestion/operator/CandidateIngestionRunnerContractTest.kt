package cloud.gearby.api.catalog.candidateingestion.operator

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Profile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@Tag("unit")
class CandidateIngestionRunnerContractTest {
    @Test
    fun `runner is profile gated to candidate ingestion`() {
        val profile = CandidateIngestionRunner::class.java.getAnnotation(Profile::class.java)

        assertEquals(listOf("candidate-ingestion"), profile.value.toList())
    }

    @Test
    fun `properties default disabled and enabled invalid config fails before provider use`() {
        assertFalse(CandidateIngestionProperties().enabled)
        assertFailsWith<IllegalArgumentException> {
            CandidateIngestionProperties(enabled = true).validate(semasServiceKey = "")
        }
        assertFailsWith<IllegalArgumentException> {
            CandidateIngestionProperties(
                enabled = true,
                provider = "semas",
                runKey = "run-1",
                allowlistVersion = "allow-v1",
                industryCodes = listOf("209006"),
            ).validate(semasServiceKey = "")
        }
    }
}
