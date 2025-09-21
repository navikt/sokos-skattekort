package no.nav.sokos.skattekort.domain.forespoersel

import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.domain.person.Person

data class Skattekortforespoersel(
    val id: SkattekortforespoerselId? = null,
    val forespoersel: Forespoersel,
    val aar: Int,
    val person: Person,
)

@Serializable
@JvmInline
value class SkattekortforespoerselId(
    val value: Long,
)
