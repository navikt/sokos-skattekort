package no.nav.sokos.skattekort.module.person

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PersonId(
    val value: Long,
)

@Serializable
data class Person(
    val id: PersonId? = null,
    val flagget: Boolean,
    val foedselsnummer: Foedselsnummer,
)
