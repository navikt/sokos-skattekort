package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable

import kotliquery.Row

import no.nav.sokos.skattekort.module.person.PersonId

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
        val skattekortDelList: List<SkattekortDel> = emptyList(),
        val tileggsopplysningList: List<Tileggsopplysning> = emptyList(),
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

data class SkattekortDel(
    val id: SkattekortDelId? = null,
    val skattekortId: SkattekortId,
    val trekkKode: String,
    val skattekortType: SkattekortType,
    val frikortBeloep: Int? = null,
    val tabellNummer: String? = null,
    val prosentsats: Double? = null,
    val antallMndForTrekk: Double? = null,
)

@Serializable
@JvmInline
value class SkattekortDelId(
    val value: Long,
)

data class Tileggsopplysning(
    val id: SkattekortTileggsopplysningId? = null,
    val skattekortId: SkattekortId,
    val opplysning: String,
)

@Serializable
@JvmInline
value class SkattekortTileggsopplysningId(
    val value: Long,
)
