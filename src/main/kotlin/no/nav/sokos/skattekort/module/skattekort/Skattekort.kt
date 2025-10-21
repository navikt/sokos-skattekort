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
        val deler: List<SkattekortDel> = emptyList(),
        val tilleggsopplysning: List<Tilleggsopplysning> = emptyList(),
    ) {
        @OptIn(ExperimentalTime::class)
        constructor(row: Row, deler: List<SkattekortDel>?, tilleggsopplysning: List<Tilleggsopplysning>) : this(
            id = SkattekortId(row.long("id")),
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

@Serializable
@JvmInline
value class SkattekortDelId(
    val value: Long,
)

interface SkattekortDel {
    companion object {
        fun create(row: Row): SkattekortDel {
            val type = row.string("type")
            return when (type) {
                "frikort" ->
                    Frikort(
                        trekkode = row.string("trekk_kode"),
                        frikortBeloep = row.int("frikort_beloep"),
                    )
                "prosent" ->
                    Prosentkort(
                        trekkode = row.string("trekk_kode"),
                        prosentSats = row.bigDecimal("prosentsats"),
                        antallMndForTrekk = row.bigDecimalOrNull("antall_mnd_for_trekk"),
                    )
                "tabell" ->
                    Tabellkort(
                        trekkode = row.string("trekk_kode"),
                        tabellNummer = row.string("tabell_nummer"),
                        prosentSats = row.bigDecimal("prosentsats"),
                        antallMndForTrekk = row.bigDecimal("antall_mnd_for_trekk"),
                    )
                else -> throw IllegalStateException("Ukjent type for skattekort-del med id ${row.long("id")}")
            }
        }
    }
}

data class Frikort(
    val trekkode: String,
    val frikortBeloep: Int,
) : SkattekortDel

data class Tabellkort(
    val trekkode: String,
    val tabellNummer: String,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal,
) : SkattekortDel

data class Prosentkort(
    val trekkode: String,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal? = null,
) : SkattekortDel

data class Tilleggsopplysning(
    val opplysning: String,
) {
    constructor(row: Row) : this(
        opplysning = row.string("opplysning"),
    )
}
