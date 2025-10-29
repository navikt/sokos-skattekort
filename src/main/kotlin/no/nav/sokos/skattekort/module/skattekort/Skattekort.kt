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
    companion object {
        fun create(row: Row): Forskuddstrekk {
            val type = ForskuddstrekkType.from(row.string("type"))
            return when (type) {
                ForskuddstrekkType.FRIKORT ->
                    Frikort(
                        trekkode = row.string("trekk_kode"),
                        frikortBeloep = row.int("frikort_beloep"),
                    )

                ForskuddstrekkType.PROSENTKORT ->
                    Prosentkort(
                        trekkode = row.string("trekk_kode"),
                        prosentSats = row.bigDecimal("prosentsats"),
                        antallMndForTrekk = row.bigDecimalOrNull("antall_mnd_for_trekk"),
                    )

                ForskuddstrekkType.TABELLKORT ->
                    Tabellkort(
                        trekkode = row.string("trekk_kode"),
                        tabellNummer = row.string("tabell_nummer"),
                        prosentSats = row.bigDecimal("prosentsats"),
                        antallMndForTrekk = row.bigDecimal("antall_mnd_for_trekk"),
                    )
            }
        }

        fun create(forskuddstrekk: no.nav.sokos.skattekort.skatteetaten.hentskattekort.Forskuddstrekk): Forskuddstrekk {
            val type = klassifiserType(forskuddstrekk)
            return when (type) {
                ForskuddstrekkType.FRIKORT ->
                    Frikort(
                        trekkode = forskuddstrekk.trekkode,
                        frikortBeloep = forskuddstrekk.frikort!!.frikortbeloep?.toInt() ?: 0,
                    )

                ForskuddstrekkType.PROSENTKORT ->
                    Prosentkort(
                        trekkode = forskuddstrekk.trekkode,
                        prosentSats = forskuddstrekk.trekkprosent!!.prosentsats,
                    )

                ForskuddstrekkType.TABELLKORT ->
                    Tabellkort(
                        trekkode = forskuddstrekk.trekkode,
                        tabellNummer = forskuddstrekk.trekktabell!!.tabellnummer,
                        prosentSats = forskuddstrekk.trekktabell.prosentsats,
                        antallMndForTrekk = forskuddstrekk.trekktabell.antallMaanederForTrekk,
                    )
            }
        }

        private fun klassifiserType(forskuddstrekk: no.nav.sokos.skattekort.skatteetaten.hentskattekort.Forskuddstrekk): ForskuddstrekkType =
            when {
                forskuddstrekk.frikort != null -> ForskuddstrekkType.FRIKORT
                forskuddstrekk.trekktabell != null -> ForskuddstrekkType.TABELLKORT
                forskuddstrekk.trekkprosent != null -> ForskuddstrekkType.PROSENTKORT
                else -> error("Forskuddstrekk ${forskuddstrekk.trekkode} har ingen av de forventede typene")
            }

        enum class ForskuddstrekkType(
            val type: String,
        ) {
            FRIKORT("frikort"),
            TABELLKORT("trekktabell"),
            PROSENTKORT("trekkprosent"),
            ;

            companion object {
                fun from(type: String): ForskuddstrekkType =
                    entries.find { it.type == type }
                        ?: error("Ukjent ForskuddstrekkType: $type")
            }
        }
    }
}

data class Frikort(
    val trekkode: String,
    val frikortBeloep: Int,
) : Forskuddstrekk

data class Tabellkort(
    val trekkode: String,
    val tabellNummer: String,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal,
) : Forskuddstrekk

data class Prosentkort(
    val trekkode: String,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal? = null,
) : Forskuddstrekk

data class Tilleggsopplysning(
    val opplysning: String,
) {
    constructor(row: Row) : this(
        opplysning = row.string("opplysning"),
    )
}
