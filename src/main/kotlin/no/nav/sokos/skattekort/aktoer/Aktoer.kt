package no.nav.sokos.skattekort.aktoer

import java.time.LocalDate

import kotlinx.serialization.Contextual
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

@Serializable
@JvmInline
value class OffNrId(
    val id: Long,
)

@Serializable
data class OffNr(
    val id: OffNrId,
    @Contextual val gjelderFom: LocalDate,
    val aktoerIdent: String,
)
