package no.nav.sokos.skattekort.person

import java.time.LocalDate

data class Foedselsnummer(
    val id: FoedselsnummerId? = null,
    val personId: PersonId? = null,
    val gjelderFom: LocalDate,
    val fnr: Personidentifikator,
)

@JvmInline
value class FoedselsnummerId(
    val value: Long,
)

@JvmInline
value class Personidentifikator(
    val value: String,
)
