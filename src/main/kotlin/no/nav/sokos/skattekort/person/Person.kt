package no.nav.sokos.skattekort.person

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AktoerId(
    val id: Long,
)

@Serializable
data class Aktoer(
    val id: AktoerId,
    val flagget: Boolean,
    val offNr: List<OffNr>,
)
