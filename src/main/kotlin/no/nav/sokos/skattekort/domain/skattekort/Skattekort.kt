package no.nav.sokos.skattekort.domain.skattekort

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable

import kotliquery.Row

import no.nav.sokos.skattekort.domain.person.PersonId

data class Skattekort
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: SkattekortId? = null,
        val personId: PersonId,
        val utstedtDato: LocalDate,
        val identifikator: String,
        val inntektsaar: Int,
        val kilde: String,
        val opprettet: Instant = Clock.System.now(),
    ) {
        @OptIn(ExperimentalTime::class)
        constructor(row: Row) : this(
            id = row.long("id")?.let { SkattekortId(it) },
            personId = PersonId(row.long("person_id")),
            utstedtDato = row.localDate("utstedt_dato").toKotlinLocalDate(),
            identifikator = row.string("identifikator"),
            inntektsaar = row.int("inntektsaar"),
            kilde = row.string("kilde"),
            opprettet = row.instant("opprettet").toKotlinInstant(),
        )
    }

@Serializable
@JvmInline
value class SkattekortId(
    val value: Long,
)
