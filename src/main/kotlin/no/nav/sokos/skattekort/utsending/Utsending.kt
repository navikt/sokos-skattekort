package no.nav.sokos.skattekort.utsending

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

import kotliquery.Row

import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.person.Personidentifikator

data class Utsending(
    val id: UtsendingId? = null,
    val fnr: Personidentifikator,
    val inntektsaar: Int,
    val forsystem: Forsystem,
    val failCount: Int = 0,
    val failMessage: String? = null,
    val opprettet: Instant = Clock.System.now(),
) {
    constructor(row: Row) : this(
        id = row.longOrNull("id")?.let { UtsendingId(it) },
        fnr = Personidentifikator(row.string("fnr")),
        inntektsaar = row.int("inntektsaar"),
        forsystem = Forsystem.fromValue(row.string("forsystem")),
        failCount = row.int("fail_count"),
        failMessage = row.stringOrNull("fail_message"),
        opprettet = row.instant("opprettet").toKotlinInstant(),
    )

    constructor(
        fnr: Personidentifikator,
        inntektsaar: Int,
        forsystem: Forsystem,
    ) : this(
        id = null,
        fnr = fnr,
        inntektsaar = inntektsaar,
        forsystem = forsystem,
    )
}

@JvmInline
value class UtsendingId(
    val value: Long,
)
