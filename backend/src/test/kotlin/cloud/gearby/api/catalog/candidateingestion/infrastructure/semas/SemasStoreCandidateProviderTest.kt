package cloud.gearby.api.catalog.candidateingestion.infrastructure.semas

import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailure
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailureCategory
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("unit")
class SemasStoreCandidateProviderTest {
    @Test
    fun `parser accepts documented flat record without inventing a GearBy category`() {
        val candidates = SemasStoreCandidateParser.parse(record("1"), "https://source.test")

        assertEquals(listOf("1"), candidates.map { it.providerRecordId })
        assertEquals("Trail 1", candidates.single().name)
        assertTrue(candidates.single().categories.isEmpty())
    }

    @Test
    fun `parser accepts explicit arrays including nested items item`() {
        assertEquals(2, SemasStoreCandidateParser.parse("[${record("1")},${record("2")} ]", "https://source.test").size)
        assertEquals(
            listOf("3"),
            SemasStoreCandidateParser
                .parse("""{"response":{"body":{"items":{"item":[${record("3")} ]}}}}""", "https://source.test")
                .map { it.providerRecordId },
        )
        assertEquals(
            listOf("4"),
            SemasStoreCandidateParser.parse("""{"items":{"item":[${record("4")} ]}}""", "https://source.test").map { it.providerRecordId },
        )
    }

    @Test
    fun `parser treats recognized empty array as an empty final page`() {
        assertEquals(emptyList(), SemasStoreCandidateParser.parse("""{"response":{"body":{"items":[]}}}""", "https://source.test"))
    }

    @Test
    fun `parser exposes documented paging metadata`() {
        val page =
            SemasStoreCandidateParser.parsePage(
                """{"response":{"body":{"totalCount":"3","pageNo":"2","numOfRows":"2","items":[${record("1")} ]}}}""",
                "https://source.test",
            )

        assertEquals(3, page.totalCount)
        assertEquals(2, page.pageNo)
        assertEquals(2, page.numOfRows)
    }

    @Test
    fun `parser rejects unknown or competing containers`() {
        assertFailsWith<ProviderFailure> {
            SemasStoreCandidateParser.parse("""{"response":{"body":{"items":{"item":${record("1")}}}}}""", "https://source.test")
        }
        assertFailsWith<ProviderFailure> {
            SemasStoreCandidateParser.parse("""{"items":[${record("1")}],"data":[${record("2")}] }""", "https://source.test")
        }
    }

    @Test
    fun `parser rejects SEMAS records with missing or non numeric coordinates`() {
        listOf(
            """
            {
              "bizesId":"missing-lon",
              "bizesNm":"Trail missing lon",
              "rdnmAdr":"Seoul Road",
              "lat":"37.000000",
              "indsSclsCd":"209006"
            }
            """.trimIndent(),
            """
            {
              "bizesId":"bad-lat",
              "bizesNm":"Trail bad lat",
              "rdnmAdr":"Seoul Road",
              "lon":"127.000000",
              "lat":"not-a-number",
              "indsSclsCd":"209006"
            }
            """.trimIndent(),
        ).forEach { body ->
            val failure = assertFailsWith<ProviderFailure> { SemasStoreCandidateParser.parse(body, "https://source.test") }

            assertEquals(ProviderFailureCategory.MALFORMED_RESPONSE, failure.category)
        }
    }

    @Test
    fun `parser rejects SEMAS records with out of range coordinates`() {
        listOf(
            """
            {
              "bizesId":"lat-91",
              "bizesNm":"Trail bad latitude",
              "rdnmAdr":"Seoul Road",
              "lon":"127.000000",
              "lat":"91.000000",
              "indsSclsCd":"209006"
            }
            """.trimIndent(),
            """
            {
              "bizesId":"lon-181",
              "bizesNm":"Trail bad longitude",
              "rdnmAdr":"Seoul Road",
              "lon":"181.000000",
              "lat":"37.000000",
              "indsSclsCd":"209006"
            }
            """.trimIndent(),
        ).forEach { body ->
            val failure = assertFailsWith<ProviderFailure> { SemasStoreCandidateParser.parse(body, "https://source.test") }

            assertEquals(ProviderFailureCategory.MALFORMED_RESPONSE, failure.category)
            assertFalse(failure.message.orEmpty().contains(body))
        }
    }

    @Test
    fun `adapter sends documented path and query and uses metadata hasNext`() =
        withServer { server, requests ->
            server.createContext("/B553077/api/open/sdsc2/storeListInUpjong") { exchange ->
                requests += TestRequest(exchange.requestURI.path, exchange.requestURI.rawQuery.toQueryMap())
                exchange.respond(
                    200,
                    """{"response":{"body":{"totalCount":"3","pageNo":"1","numOfRows":"2","items":[${record("1")},${record("2")}]}}}""",
                )
            }
            val provider = provider(server)

            val page = provider.fetchPage("209006", pageNo = 1, pageSize = 2)

            assertEquals("/B553077/api/open/sdsc2/storeListInUpjong", requests.single().path)
            assertEquals(
                mapOf(
                    "ServiceKey" to "test-key",
                    "divId" to "indsSclsCd",
                    "key" to "209006",
                    "pageNo" to "1",
                    "numOfRows" to "2",
                    "type" to "json",
                ),
                requests.single().query,
            )
            assertTrue(page.hasNext)
        }

    @Test
    fun `adapter maps provider status without exposing response body`() {
        listOf(
            401 to ProviderFailureCategory.AUTH,
            403 to ProviderFailureCategory.AUTH,
            429 to ProviderFailureCategory.QUOTA,
        ).forEach { (status, category) ->
            withServer { server, _ ->
                server.createContext("/B553077/api/open/sdsc2/storeListInUpjong") { exchange ->
                    exchange.respond(status, "secret-body")
                }
                val failure = assertFailsWith<ProviderFailure> { provider(server).fetchPage("209006", pageNo = 1, pageSize = 1) }

                assertEquals(category, failure.category)
                assertFalse(failure.message.orEmpty().contains("secret-body"))
            }
        }
    }

    private fun record(id: String) =
        """{
            "bizesId":"$id",
            "bizesNm":"Trail $id",
            "rdnmAdr":"Seoul Road $id",
            "lon":"127.00000$id",
            "lat":"37.00000$id",
            "indsSclsCd":"209006"
        }"""

    private fun provider(server: HttpServer) =
        SemasStoreCandidateProvider(
            SemasIngestionProperties(serviceKey = "test-key", baseUrl = "http://127.0.0.1:${server.address.port}"),
            RestClient.builder(),
        )

    private fun withServer(block: (HttpServer, MutableList<TestRequest>) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<TestRequest>()
        try {
            server.start()
            block(server, requests)
        } finally {
            server.stop(0)
        }
    }

    private data class TestRequest(
        val path: String,
        val query: Map<String, String>,
    )

    private fun String.toQueryMap(): Map<String, String> =
        split("&").associate { part ->
            val pieces = part.split("=", limit = 2)
            URLDecoder.decode(pieces[0], Charsets.UTF_8) to URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8)
        }

    private fun HttpExchange.respond(
        status: Int,
        body: String,
    ) {
        sendResponseHeaders(status, body.toByteArray().size.toLong())
        responseBody.use { it.write(body.toByteArray()) }
    }
}
