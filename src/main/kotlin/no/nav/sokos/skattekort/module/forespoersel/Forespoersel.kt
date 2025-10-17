package no.nav.sokos.skattekort.module.forespoersel

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class Forespoersel
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: ForespoerselId? = null,
        val dataMottatt: String,
        val forsystem: Forsystem,
        val opprettet: Instant = Clock.System.now(),
    )

@Serializable
@JvmInline
value class ForespoerselId(
    val value: Long,
)
