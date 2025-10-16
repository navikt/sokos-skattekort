package no.nav.sokos.skattekort.module.person

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Foedselsnummer(
    val id: FoedselsnummerId? = null,
    val personId: PersonId? = null,
    val gjelderFom: LocalDate,
    val fnr: Personidentifikator,
)

@Serializable
@JvmInline
value class FoedselsnummerId(
    val value: Long,
)

@Serializable
@JvmInline
value class Personidentifikator(
    val value: String,
)
