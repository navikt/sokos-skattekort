package no.nav.sokos.skattekort.forespoersel

import java.time.Instant

data class Forespoersel(
    val id: ForespoerselId? = null,
    val dataMottatt: String,
    val forsystem: Forsystem,
    val opprettet: Instant = Instant.now(),
)

@JvmInline
value class ForespoerselId(
    val value: Long,
)
