package no.nav.sokos.skattekort.module.utsending

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.Serializable

import kotliquery.Row

import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.person.Personidentifikator

data class Utsending
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: UtsendingId? = null,
        val fnr: Personidentifikator,
        val inntektsaar: Int,
        val forsystem: Forsystem,
        val failCount: Int = 0,
        val failMessage: String? = null,
        val opprettet: Instant = Clock.System.now(),
    ) {
        @OptIn(ExperimentalTime::class)
        constructor(row: Row) : this(
            id = row.long("id")?.let { UtsendingId(it) },
            fnr = Personidentifikator(row.string("fnr")),
            inntektsaar = row.int("inntektsaar"),
            forsystem = Forsystem.fromValue(row.string("forsystem")),
            failCount = row.int("fail_count"),
            failMessage = row.stringOrNull("fail_message"),
            opprettet = row.instant("opprettet").toKotlinInstant(),
        )
    }

@Serializable
@JvmInline
value class UtsendingId(
    val value: Long,
)
