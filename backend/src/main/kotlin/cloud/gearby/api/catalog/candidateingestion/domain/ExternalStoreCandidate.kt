package cloud.gearby.api.catalog.candidateingestion.domain

import cloud.gearby.api.catalog.domain.Category
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

private val spaces = Regex("\\s+")

data class ExternalStoreCandidate(
    val providerRecordId: String? = null,
    val name: String,
    val roadAddress: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val phone: String? = null,
    val industryCode: String? = null,
    val sourceUrl: String,
    val categories: Set<Category>,
    val payloadSha256Digest: String,
) {
    fun normalized(): NormalizedStoreCandidate {
        val normalizedName = normalize(name)
        val normalizedAddress = roadAddress?.let(::normalize)?.takeIf(String::isNotBlank)
        val lat = latitude?.setScale(6, RoundingMode.HALF_UP)
        val lon = longitude?.setScale(6, RoundingMode.HALF_UP)
        val id = providerRecordId?.trim()?.takeIf(String::isNotEmpty)
        val identity =
            when {
                id != null -> CandidateIdentity(CandidateMatchPrecedence.PROVIDER_RECORD, "provider:${normalize(id)}")
                normalizedAddress != null ->
                    CandidateIdentity(
                        CandidateMatchPrecedence.NAME_ADDRESS,
                        "name-address:$normalizedName|$normalizedAddress",
                    )
                lat != null && lon != null ->
                    CandidateIdentity(
                        CandidateMatchPrecedence.NAME_COORDINATES,
                        "name-coordinates:$normalizedName|$lat|$lon",
                    )
                else -> CandidateIdentity(CandidateMatchPrecedence.AMBIGUOUS, "ambiguous:${sha256("$normalizedName|${sourceUrl.trim()}")}")
            }
        return NormalizedStoreCandidate(
            providerRecordId = id,
            normalizedName = normalizedName,
            roadAddress = normalizedAddress,
            roundedLatitude = lat,
            roundedLongitude = lon,
            phone = phone?.trim()?.takeIf(String::isNotEmpty),
            industryCode = industryCode?.trim()?.takeIf(String::isNotEmpty),
            sourceUrl = sourceUrl.trim(),
            categories = categories,
            payloadSha256Digest = payloadSha256Digest.lowercase(),
            identity = identity,
        )
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(spaces, " ")

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
