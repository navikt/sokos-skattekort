package no.nav.sokos.skattekort.person

import java.time.LocalDate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class OffNr(
    val id: OffNrId,
    @Contextual val gjelderFom: LocalDate,
    val personIdent: String,
)

@Serializable
@JvmInline
value class OffNrId(
    val id: Long,
)
