package no.nav.sokos.skattekort.person

@JvmInline
value class PersonId(
    val value: Long,
)

data class Person(
    val id: PersonId? = null,
    val flagget: Boolean,
    val foedselsnummer: Foedselsnummer,
)
