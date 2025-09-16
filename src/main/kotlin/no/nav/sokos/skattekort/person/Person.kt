package no.nav.sokos.skattekort.person

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PersonId(
    val id: Long,
)

@Serializable
data class Person(
    val id: PersonId,
    val flagget: Boolean,
    val fnrs: List<Foedselsnummer>,
)
