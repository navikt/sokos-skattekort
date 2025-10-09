package no.nav.sokos.skattekort.domain.skattekort

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.domain.person.PersonId
import no.nav.sokos.skattekort.domain.person.Personidentifikator

data class Skattekort
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: SkattekortId? = null,
        val personId: PersonId,
        val fnr: Personidentifikator,
        val inntektsaar: Int,
        val dataMottatt: String,
        val opprettet: Instant = Clock.System.now(),
    )

@Serializable
@JvmInline
value class SkattekortId(
    val value: Long,
)
