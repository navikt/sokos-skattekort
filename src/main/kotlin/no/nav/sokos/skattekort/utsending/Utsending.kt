package no.nav.sokos.skattekort.utsending

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

import kotliquery.Row

import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.SkattekortId

data class Utsending(
    val id: UtsendingId? = null,
    val fnr: Personidentifikator,
    val inntektsaar: Int,
    val forsystem: Forsystem,
    val failCount: Int = 0,
    val failMessage: String? = null,
    val opprettet: Instant = Clock.System.now(),
    val skattekortId: SkattekortId,
) {
    constructor(row: Row) : this(
        id = row.longOrNull("id")?.let { UtsendingId(it) },
        fnr = Personidentifikator(row.string("fnr")),
        inntektsaar = row.int("inntektsaar"),
        forsystem = Forsystem.fromValue(row.string("forsystem")),
        failCount = row.int("fail_count"),
        failMessage = row.stringOrNull("fail_message"),
        opprettet = row.instant("opprettet").toKotlinInstant(),
        skattekortId = SkattekortId(row.long("skattekort_id")),
    )

    constructor(
        fnr: Personidentifikator,
        inntektsaar: Int,
        forsystem: Forsystem,
        skattekortId: SkattekortId,
    ) : this(
        id = null,
        fnr = fnr,
        inntektsaar = inntektsaar,
        forsystem = forsystem,
        skattekortId = skattekortId,
    )
}

@JvmInline
value class UtsendingId(
    val value: Long,
)
