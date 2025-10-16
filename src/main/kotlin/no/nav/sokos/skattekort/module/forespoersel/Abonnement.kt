package no.nav.sokos.skattekort.module.forespoersel

import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.module.person.Person

data class Abonnement(
    val id: AbonnementId? = null,
    val forespoersel: Forespoersel,
    val inntektsaar: Int,
    val person: Person,
)

@Serializable
@JvmInline
value class AbonnementId(
    val value: Long,
)
