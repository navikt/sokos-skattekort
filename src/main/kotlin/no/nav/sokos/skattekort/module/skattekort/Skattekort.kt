package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal

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
        val forskuddstrekkList: List<Forskuddstrekk> = emptyList(),
        val tilleggsopplysningList: List<Tilleggsopplysning> = emptyList(),
    ) {
        @OptIn(ExperimentalTime::class)
        constructor(row: Row, forskuddstrekkList: List<Forskuddstrekk>, tilleggsopplysningList: List<Tilleggsopplysning>) : this(
            id = SkattekortId(row.long("id")),
            personId = PersonId(row.long("person_id")),
            utstedtDato = row.localDate("utstedt_dato").toKotlinLocalDate(),
            identifikator = row.string("identifikator"),
            inntektsaar = row.int("inntektsaar"),
            kilde = row.string("kilde"),
            opprettet = row.instant("opprettet").toKotlinInstant(),
            forskuddstrekkList = forskuddstrekkList,
            tilleggsopplysningList = tilleggsopplysningList,
        )
    }

@Serializable
@JvmInline
value class SkattekortId(
    val value: Long,
)

interface Forskuddstrekk {
    val trekkode: String
}

data class Frikort(
    override val trekkode: String,
    val frikortBeloep: Int,
) : Forskuddstrekk {
    constructor(row: Row) : this(
        trekkode = row.string("trekk_kode"),
        frikortBeloep = row.int("frikort_beloep"),
    )
}

data class Tabellkort(
    override val trekkode: String,
    val tabellNummer: String,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal,
) : Forskuddstrekk {
    constructor(row: Row) : this(
        trekkode = row.string("trekk_kode"),
        tabellNummer = row.string("tabell_nummer"),
        prosentSats = row.bigDecimal("prosentsats"),
        antallMndForTrekk = row.bigDecimal("antall_mnd_for_trekk"),
    )
}

data class Prosentkort(
    override val trekkode: String,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal? = null,
) : Forskuddstrekk {
    constructor(row: Row) : this(
        trekkode = row.string("trekk_kode"),
        prosentSats = row.bigDecimal("prosentsats"),
        antallMndForTrekk = row.bigDecimalOrNull("antall_mnd_for_trekk"),
    )
}

data class Tilleggsopplysning(
    val opplysning: String,
) {
    constructor(row: Row) : this(
        opplysning = row.string("opplysning"),
    )
}

fun mapToForskuddstrekk(row: Row): Forskuddstrekk =
    when (row.string("type")) {
        "frikort" -> Frikort(row)
        "prosent" -> Prosentkort(row)
        "tabell" -> Tabellkort(row)
        else -> throw IllegalStateException("Ukjent type for skattekort-del med id ${row.long("id")}")
    }
