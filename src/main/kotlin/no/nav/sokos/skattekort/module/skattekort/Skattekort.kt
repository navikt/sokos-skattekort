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
import mu.KotlinLogging

import no.nav.sokos.skattekort.module.person.PersonId

enum class ResultatForSkattekort(
    val value: String,
) {
    IkkeSkattekort(value = "ikkeSkattekort"),
    IkkeTrekkplikt(value = "ikkeTrekkplikt"),
    SkattekortopplysningerOK(value = "skattekortopplysningerOK"),
    UgyldigOrganisasjonsnummer(value = "ugyldigOrganisasjonsnummer"),
    UgyldigFoedselsEllerDnummer(value = "ugyldigFoedselsEllerDnummer"),
    UtgaattDnummerSkattekortForFoedselsnummerErLevert(value = "utgaattDnummerSkattekortForFoedselsnummerErLevert"),
    ;

    companion object {
        private val logger = KotlinLogging.logger {}

        fun fromValue(value: String): ResultatForSkattekort {
            try {
                return ResultatForSkattekort.entries.first { it.value == value }
            } catch (e: NoSuchElementException) {
                logger.error("Ukjent ResultatForSkattekort-verdi funnet: $value")
                throw e
            }
        }
    }
}

data class Skattekort
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: SkattekortId? = null,
        val personId: PersonId,
        val utstedtDato: LocalDate?,
        val identifikator: String?,
        val inntektsaar: Int,
        val kilde: String,
        val resultatForSkattekort: ResultatForSkattekort,
        val opprettet: Instant = Clock.System.now(),
        val forskuddstrekkList: List<Forskuddstrekk> = emptyList(),
        val tilleggsopplysningList: List<Tilleggsopplysning> = emptyList(),
    ) {
        @OptIn(ExperimentalTime::class)
        constructor(row: Row, forskuddstrekkList: List<Forskuddstrekk>, tilleggsopplysningList: List<Tilleggsopplysning>) : this(
            id = SkattekortId(row.long("id")),
            personId = PersonId(row.long("person_id")),
            utstedtDato = row.localDateOrNull("utstedt_dato")?.toKotlinLocalDate(),
            identifikator = row.stringOrNull("identifikator"),
            inntektsaar = row.int("inntektsaar"),
            kilde = row.string("kilde"),
            resultatForSkattekort = ResultatForSkattekort.fromValue(row.string("resultatForSkattekort")),
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

sealed interface Forskuddstrekk {
    fun trekkode(): Trekkode =
        when (this) {
            is Frikort -> this.trekkode
            is Prosentkort -> this.trekkode
            is Tabellkort -> this.trekkode
        }

    fun requiresAdminRole(): Boolean = trekkode().requiresAdminRole

    companion object {
        fun create(row: Row): Forskuddstrekk {
            val type = ForskuddstrekkType.from(row.string("type"))
            return when (type) {
                ForskuddstrekkType.FRIKORT ->
                    Frikort(
                        trekkode = Trekkode.from(row.string("trekk_kode")),
                        frikortBeloep = row.int("frikort_beloep"),
                    )

                ForskuddstrekkType.PROSENTKORT ->
                    Prosentkort(
                        trekkode = Trekkode.from(row.string("trekk_kode")),
                        prosentSats = row.bigDecimal("prosentsats"),
                        antallMndForTrekk = row.bigDecimalOrNull("antall_mnd_for_trekk"),
                    )

                ForskuddstrekkType.TABELLKORT ->
                    Tabellkort(
                        trekkode = Trekkode.from(row.string("trekk_kode")),
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
                        trekkode = Trekkode.from(forskuddstrekk.trekkode),
                        frikortBeloep = forskuddstrekk.frikort!!.frikortbeloep?.toInt() ?: 0,
                    )

                ForskuddstrekkType.PROSENTKORT ->
                    Prosentkort(
                        trekkode = Trekkode.from(forskuddstrekk.trekkode),
                        prosentSats = forskuddstrekk.trekkprosent!!.prosentsats,
                    )

                ForskuddstrekkType.TABELLKORT ->
                    Tabellkort(
                        trekkode = Trekkode.from(forskuddstrekk.trekkode),
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
    val trekkode: Trekkode,
    val frikortBeloep: Int,
) : Forskuddstrekk

data class Tabellkort(
    val trekkode: Trekkode,
    val tabellNummer: String,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal,
) : Forskuddstrekk

data class Prosentkort(
    val trekkode: Trekkode,
    val prosentSats: BigDecimal,
    val antallMndForTrekk: BigDecimal? = null,
) : Forskuddstrekk

enum class Tilleggsopplysning(
    val value: String,
    val requiresAdminRole: Boolean,
) {
    OPPHOLD_PAA_SVALBARD("oppholdPaaSvalbard", false),
    KILDESKATT_PAA_PENSJON("kildeskattPaaPensjon", false),
    OPPHOLD_I_TILTAKSSONE("oppholdITiltakssone", false),
    KILDESKATT_PAA_LOENN("kildeskattPaaLoenn", true),
    ;

    companion object {
        private val logger = KotlinLogging.logger {}

        fun fromValue(value: String): Tilleggsopplysning {
            try {
                return Tilleggsopplysning.entries.first { it.value == value }
            } catch (e: NoSuchElementException) {
                logger.error("Ukjent tilleggsopplysning funnet: $value")
                throw e
            }
        }
    }
}

enum class SkattekortKilde(
    val value: String,
) {
    SKATTEETATEN(value = "skatteetaten"),
    SYNTETISERT(value = "syntetisert"),
    MANGLER(value = "mangler"),
}

enum class Trekkode(
    val value: String,
    val requiresAdminRole: Boolean,
) {
    LOENN_FRA_HOVEDARBEIDSGIVER("loennFraHovedarbeidsgiver", true),
    LOENN_FRA_BIARBEIDSGIVER("loennFraBiarbeidsgiver", true),
    LOENN_FRA_NAV("loennFraNAV", false),
    PENSJON("pensjon", true),
    PENSJON_FRA_NAV("pensjonFraNAV", false),
    LOENN_TIL_UTENRIKSTJENESTEMANN("loennTilUtenrikstjenestemann", true),
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER("loennKunTrygdeavgiftTilUtenlandskBorger", true),
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER_SOM_GRENSEGJENGER("loennKunTrygdeavgiftTilUtenlandskBorgerSomGrensegjenger", true),
    UFOERETRYGD_FRA_NAV("ufoeretrygdFraNAV", false),
    UFOEREYTELSER_FRA_ANDRE("ufoereytelserFraAndre", true),
    INTRODUKSJONSSTOENAD("introduksjonsstoenad", true),
    ;

    companion object {
        fun from(kode: String): Trekkode = entries.find { it.value == kode } ?: error("Ukjent trekkode: $kode")
    }
}
