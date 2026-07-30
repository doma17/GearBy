package cloud.gearby.api.catalog.candidateingestion.infrastructure.semas

import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailure
import cloud.gearby.api.catalog.candidateingestion.application.port.ProviderFailureCategory
import cloud.gearby.api.catalog.candidateingestion.domain.ExternalStoreCandidate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.security.MessageDigest

object SemasStoreCandidateParser {
    private val mapper = ObjectMapper()
    private val recordKeys = setOf("bizesId", "bizesNm", "rdnmAdr", "lon", "lat", "indsSclsCd", "ksicCd")

    fun parse(
        json: String,
        sourceUrl: String,
    ): List<ExternalStoreCandidate> = parsePage(json, sourceUrl).candidates

    fun parsePage(
        json: String,
        sourceUrl: String,
    ): SemasParsedPage {
        val root =
            try {
                mapper.readTree(json)
            } catch (ex: Exception) {
                throw ProviderFailure(ProviderFailureCategory.MALFORMED_RESPONSE, "SEMAS response is not valid JSON", ex)
            }
        return SemasParsedPage(
            candidates = records(root).map { it.toCandidate(sourceUrl) },
            totalCount = root.metadataInt("totalCount"),
            pageNo = root.metadataInt("pageNo"),
            numOfRows = root.metadataInt("numOfRows"),
        )
    }

    private fun records(root: JsonNode): List<JsonNode> =
        when {
            root.isArray() -> root.toList()
            root.isObject() && root.hasRecordFields() -> listOf(root)
            else -> explicitArrays(root).singleOrNull()?.toList() ?: unsupported()
        }

    private fun explicitArrays(root: JsonNode): List<JsonNode> =
        listOf(
            root.get("items"),
            root.path("items").get("item"),
            root.get("data"),
            root.path("body").get("items"),
            root.path("body").path("items").get("item"),
            root.path("response").path("body").get("items"),
            root
                .path("response")
                .path("body")
                .path("items")
                .get("item"),
        ).mapNotNull { node -> node?.takeIf { it.isArray() } }

    private fun unsupported(): Nothing =
        throw ProviderFailure(ProviderFailureCategory.MALFORMED_RESPONSE, "SEMAS response shape is unsupported")

    private fun JsonNode.toCandidate(sourceUrl: String): ExternalStoreCandidate {
        val name = text("bizesNm") ?: throw ProviderFailure(ProviderFailureCategory.MALFORMED_RESPONSE, "SEMAS record is missing bizesNm")
        val id = text("bizesId")
        val lon = decimal("lon")
        val lat = decimal("lat")
        val industryCode = text("indsSclsCd") ?: text("ksicCd")
        return ExternalStoreCandidate(
            providerRecordId = id,
            name = name,
            roadAddress = text("rdnmAdr"),
            latitude = lat,
            longitude = lon,
            industryCode = industryCode,
            sourceUrl = sourceUrl,
            categories = emptySet(),
            payloadSha256Digest = digest(allowedDigestFields()),
        )
    }

    private fun JsonNode.metadataInt(name: String): Int? =
        sequenceOf(this, path("body"), path("response").path("body"))
            .mapNotNull { it.text(name)?.toIntOrNull() }
            .firstOrNull()

    private fun JsonNode.hasRecordFields(): Boolean = get("bizesNm") != null || get("bizesId") != null

    private fun JsonNode.text(name: String): String? = get(name)?.asString()?.trim()?.takeIf(String::isNotBlank)

    private fun JsonNode.decimal(name: String): BigDecimal {
        val value =
            text(name)?.toBigDecimalOrNull()
                ?: throw ProviderFailure(ProviderFailureCategory.MALFORMED_RESPONSE, "SEMAS record is missing or invalid $name")
        val validRange =
            when (name) {
                "lat" -> value in BigDecimal("-90")..BigDecimal("90")
                "lon" -> value in BigDecimal("-180")..BigDecimal("180")
                else -> true
            }
        if (!validRange) throw ProviderFailure(ProviderFailureCategory.MALFORMED_RESPONSE, "SEMAS record has out of range $name")
        return value
    }

    private fun JsonNode.allowedDigestFields(): String = recordKeys.sorted().joinToString("|") { key -> "$key=${text(key).orEmpty()}" }

    private fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
