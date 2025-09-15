package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDateTime

data class Forespoersel(
    val forsystem: Forsystem,
    val opprettet: LocalDateTime,
    val data_mottatt: String,
)
