package no.nav.sokos.skattekort.skattekort

import java.math.BigDecimal
import java.math.RoundingMode

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate

import kotliquery.Row
import mu.KotlinLogging

import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.person.PersonId

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
                return entries.first { it.value == value }
            } catch (e: NoSuchElementException) {
                logger.error("Ukjent ResultatForSkattekort-verdi funnet: $value")
                throw e
            }
        }
    }
}

data class Skattekort(
    val id: SkattekortId? = null,
    val generertFra: SkattekortId? = null,
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
    constructor(row: Row, forskuddstrekkList: List<Forskuddstrekk>, tilleggsopplysningList: List<Tilleggsopplysning>) : this(
        id = SkattekortId(row.long("id")),
        generertFra = row.longOrNull("generert_fra")?.let { SkattekortId(it) },
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

    constructor(
        personId: PersonId,
        arbeidstaker: Arbeidstaker,
    ) : this(
        personId = personId,
        utstedtDato = arbeidstaker.skattekort?.utstedtDato?.let(LocalDate::parse),
        identifikator = arbeidstaker.skattekort?.skattekortidentifikator?.toString(),
        inntektsaar = arbeidstaker.inntektsaar,
        kilde = SkattekortKilde.SKATTEETATEN.value,
        resultatForSkattekort = ResultatForSkattekort.fromValue(arbeidstaker.resultatForSkattekort),
        forskuddstrekkList = arbeidstaker.skattekort?.forskuddstrekk?.map(Forskuddstrekk::create) ?: emptyList(),
        tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning.fromValue(it) } ?: emptyList(),
    )
}

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
                ForskuddstrekkType.FRIKORT -> {
                    Frikort(
                        trekkode = Trekkode.fromValue(row.string("trekk_kode")),
                        frikortBeloep = row.intOrNull("frikort_beloep"),
                    )
                }

                ForskuddstrekkType.PROSENTKORT -> {
                    Prosentkort(
                        trekkode = Trekkode.fromValue(row.string("trekk_kode")),
                        prosentSats = row.bigDecimal("prosentsats").setScale(2, RoundingMode.HALF_UP),
                        antallMndForTrekk = row.bigDecimalOrNull("antall_mnd_for_trekk")?.setScale(1, RoundingMode.HALF_UP),
                    )
                }

                ForskuddstrekkType.TABELLKORT -> {
                    Tabellkort(
                        trekkode = Trekkode.fromValue(row.string("trekk_kode")),
                        tabellNummer = row.string("tabell_nummer"),
                        prosentSats = row.bigDecimal("prosentsats").setScale(2, RoundingMode.HALF_UP),
                        antallMndForTrekk = row.bigDecimal("antall_mnd_for_trekk").setScale(1, RoundingMode.HALF_UP),
                    )
                }
            }
        }

        fun create(forskuddstrekk: no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Forskuddstrekk): Forskuddstrekk {
            val type = klassifiserType(forskuddstrekk)
            return when (type) {
                ForskuddstrekkType.FRIKORT -> {
                    Frikort(
                        trekkode = Trekkode.fromValue(forskuddstrekk.trekkode),
                        frikortBeloep = forskuddstrekk.frikort!!.frikortbeloep?.toInt(),
                    )
                }

                ForskuddstrekkType.PROSENTKORT -> {
                    Prosentkort(
                        trekkode = Trekkode.fromValue(forskuddstrekk.trekkode),
                        prosentSats = forskuddstrekk.trekkprosent!!.prosentsats,
                    )
                }

                ForskuddstrekkType.TABELLKORT -> {
                    Tabellkort(
                        trekkode = Trekkode.fromValue(forskuddstrekk.trekkode),
                        tabellNummer = forskuddstrekk.trekktabell!!.tabellnummer,
                        prosentSats = forskuddstrekk.trekktabell.prosentsats,
                        antallMndForTrekk = forskuddstrekk.trekktabell.antallMaanederForTrekk,
                    )
                }
            }
        }

        private fun klassifiserType(forskuddstrekk: no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Forskuddstrekk): ForskuddstrekkType =
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
    val frikortBeloep: Int?,
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
                return entries.first { it.value == value }
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
    MANUELL(value = "manuell"),
    ;

    companion object {
        fun fromValue(kode: String): SkattekortKilde = SkattekortKilde.entries.find { it.value == kode } ?: error("Ukjent kilde: $kode")
    }
}

enum class Trekkode(
    val value: String,
    val requiresAdminRole: Boolean,
) {
    // Trekkodene vi har behov for
    LOENN_FRA_NAV("loennFraNAV", false),
    PENSJON_FRA_NAV("pensjonFraNAV", false),
    UFOERETRYGD_FRA_NAV("ufoeretrygdFraNAV", false),

    // Trekkodene vi ikke har behov for
    INTRODUKSJONSSTOENAD("introduksjonsstoenad", true),
    LOENN_FRA_BIARBEIDSGIVER("loennFraBiarbeidsgiver", true),
    LOENN_FRA_HOVEDARBEIDSGIVER("loennFraHovedarbeidsgiver", true),
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER("loennKunTrygdeavgiftTilUtenlandskBorger", true),
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER_SOM_GRENSEGJENGER("loennKunTrygdeavgiftTilUtenlandskBorgerSomGrensegjenger", true),
    LOENN_TIL_UTENRIKSTJENESTEMANN("loennTilUtenrikstjenestemann", true),
    PENSJON("pensjon", true),
    UFOEREYTELSER_FRA_ANDRE("ufoereytelserFraAndre", true),
    ;

    companion object {
        fun fromValue(kode: String): Trekkode = entries.find { it.value == kode } ?: error("Ukjent trekkode: $kode")
    }
}
