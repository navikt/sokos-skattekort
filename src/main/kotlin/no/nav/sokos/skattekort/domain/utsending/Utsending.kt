package no.nav.sokos.skattekort.domain.utsending

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.domain.forespoersel.AbonnementId
import no.nav.sokos.skattekort.domain.forespoersel.Forsystem
import no.nav.sokos.skattekort.domain.person.Personidentifikator

data class Utsending
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: UtsendingId? = null,
        val abonnementId: AbonnementId,
        val fnr: Personidentifikator,
        val inntektsaar: Int,
        val forsystem: Forsystem,
        val opprettet: Instant = Clock.System.now(),
    )

@Serializable
@JvmInline
value class UtsendingId(
    val value: Long,
)
