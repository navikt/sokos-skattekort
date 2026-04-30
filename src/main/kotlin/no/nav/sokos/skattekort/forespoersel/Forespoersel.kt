package no.nav.sokos.skattekort.forespoersel

import kotlin.time.Clock
import kotlin.time.Instant

data class Forespoersel(
    val id: ForespoerselId? = null,
    val dataMottatt: String,
    val forsystem: Forsystem,
    val opprettet: Instant = Clock.System.now(),
)

@JvmInline
value class ForespoerselId(
    val value: Long,
)
