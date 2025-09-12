package no.nav.sokos.skattekort.person

import java.time.LocalDate

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class Foedselsnummer(
    val id: FoedselsnummerId,
    @Contextual val gjelderFom: LocalDate,
    val fnr: String,
)

@Serializable
@JvmInline
value class FoedselsnummerId(
    val id: Long,
)
