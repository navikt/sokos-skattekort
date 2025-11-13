package no.nav.sokos.skattekort.api.skattekortpersonapi.v1

import java.math.BigDecimal

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.BigDecimalJson
import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort

@Serializable
data class SkattekortTilArbeidsgiver(
    var navn: String? = null,
    val arbeidsgiver: List<Arbeidsgiver>,
)

@Serializable
data class Arbeidsgiver(
    val arbeidstaker: List<Arbeidstaker>,
    val arbeidsgiveridentifikator: IdentifikatorForEnhetEllerPerson,
)

@Serializable
data class Arbeidstaker(
    val inntektsaar: Long,
    val arbeidstakeridentifikator: String,
    val resultatPaaForespoersel: Resultatstatus,
    val skattekort: Skattekort? = null,
    val tilleggsopplysning: List<Tilleggsopplysning>? = null,
) {
    constructor(inntektsaar: Long, fnr: String, sk: no.nav.sokos.skattekort.module.skattekort.Skattekort) : this(
        inntektsaar = inntektsaar,
        arbeidstakeridentifikator = fnr,
        resultatPaaForespoersel = Resultatstatus.fromDomainModel(sk.resultatForSkattekort),
        skattekort =
            Skattekort(
                utstedtDato = sk.utstedtDato,
                skattekortidentifikator = sk.identifikator?.toLong(), // TODO: Should be refactored to match types.
                forskuddstrekk = sk.forskuddstrekkList.map { Forskuddstrekk.fromDomainModel(it) },
            ),
        tilleggsopplysning = sk.tilleggsopplysningList.map { Tilleggsopplysning.fromDomainModel(it) },
    )
}

@Serializable
data class IdentifikatorForEnhetEllerPerson(
    val organisasjonsnummer: String? = null,
    val personidentifikator: String? = null,
)

@Serializable
enum class Resultatstatus(
    val value: String,
) {
    @SerialName("ikkeSkattekort")
    IKKE_SKATTEKORT("ikkeSkattekort"),

    @SerialName("vurderArbeidstillatelse")
    VURDER_ARBEIDSTILLATELSE("vurderArbeidstillatelse"),

    @SerialName("ikkeTrekkplikt")
    IKKE_TREKKPLIKT("ikkeTrekkplikt"),

    @SerialName("skattekortopplysningerOK")
    SKATTEKORTOPPLYSNINGER_OK("skattekortopplysningerOK"),

    @SerialName("ugyldigOrganisasjonsnummer")
    UGYLDIG_ORGANISASJONSNUMMER("ugyldigOrganisasjonsnummer"),

    @SerialName("ugyldigFoedselsEllerDnummer")
    UGYLDIG_FOEDSELS_ELLER_DNUMMER("ugyldigFoedselsEllerDnummer"),

    @SerialName("utgaattDnummerSkattekortForFoedselsnummerErLevert")
    UTGAATT_DNUMMER_SKATTEKORT_FOR_FOEDSELSNUMMER_ER_LEVERT("utgaattDnummerSkattekortForFoedselsnummerErLevert"),
    ;

    companion object {
        fun fromDomainModel(resultat: ResultatForSkattekort) =
            entries.find { it.value == resultat.value }
                ?: throw IllegalArgumentException("Ukjent skattekort-status ${resultat.value}")
    }
}

@Serializable
enum class Tilleggsopplysning(
    val value: String,
) {
    @SerialName("oppholdPaaSvalbard")
    OPPHOLD_PAA_SVALBARD("oppholdPaaSvalbard"),

    @SerialName("kildeskattpensjonist")
    KILDESKATTPENSJONIST("kildeskattpensjonist"),

    @SerialName("oppholdITiltakssone")
    OPPHOLD_I_TILTAKSSONE("oppholdITiltakssone"),

    @SerialName("kildeskattPaaLoenn")
    KILDESKATT_PAA_LOENN("kildeskattPaaLoenn"),
    ;

    companion object {
        fun fromDomainModel(tilleggsopplysning: no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning): Tilleggsopplysning =
            entries.find { it.value == tilleggsopplysning.opplysning } ?: throw IllegalArgumentException("Ukjent tilleggsopplysning ${tilleggsopplysning.opplysning}")
    }
}

@Serializable
data class Skattekort(
    val utstedtDato: LocalDate? = null,
    val skattekortidentifikator: Long? = null,
    val forskuddstrekk: List<Forskuddstrekk>? = null,
)

@Serializable
@SerialName("forskuddstrekk")
sealed interface Forskuddstrekk {
    val trekkode: Trekkode

    companion object {
        fun fromDomainModel(forskuddstrekk: no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk): Forskuddstrekk =
            when (forskuddstrekk) {
                is no.nav.sokos.skattekort.module.skattekort.Frikort ->
                    Frikort(
                        Trekkode.fromValue(forskuddstrekk.trekkode),
                        BigDecimal(forskuddstrekk.frikortBeloep),
                    )
                is Tabellkort ->
                    Trekktabell(
                        trekkode = Trekkode.fromValue(forskuddstrekk.trekkode),
                        tabellnummer = forskuddstrekk.tabellNummer,
                        prosentsats = forskuddstrekk.prosentSats,
                        antallMaanederForTrekk = forskuddstrekk.antallMndForTrekk,
                    )
                is Prosentkort ->
                    Trekkprosent(
                        trekkode = Trekkode.fromValue(forskuddstrekk.trekkode),
                        prosentsats = forskuddstrekk.prosentSats,
                        antallMaanederForTrekk = forskuddstrekk.antallMndForTrekk,
                    )
            }
    }
}

@Serializable
@SerialName("Frikort")
data class Frikort(
    override val trekkode: Trekkode,
    val frikortbeloep: BigDecimalJson? = null,
) : Forskuddstrekk

@Serializable
@SerialName("Trekktabell")
data class Trekktabell(
    override val trekkode: Trekkode,
    // val tabelltype: Tabelltype? = null, Returneres ikke lenger av skatt
    val tabellnummer: String? = null,
    val prosentsats: BigDecimalJson? = null,
    val antallMaanederForTrekk: BigDecimalJson? = null,
) : Forskuddstrekk

@Serializable
@SerialName("Trekkprosent")
data class Trekkprosent(
    override val trekkode: Trekkode,
    val prosentsats: BigDecimalJson? = null,
    var antallMaanederForTrekk: BigDecimalJson? = null,
) : Forskuddstrekk

@Serializable
enum class Trekkode(
    val value: String,
) {
    LOENN_FRA_HOVEDARBEIDSGIVER("loennFraHovedarbeidsgiver"),
    LOENN_FRA_BIARBEIDSGIVER("loennFraBiarbeidsgiver"),
    LOENN_FRA_NAV("loennFraNAV"),
    PENSJON("pensjon"),
    PENSJON_FRA_NAV("pensjonFraNAV"),
    LOENN_TIL_UTENRIKSTJENESTEMANN("loennTilUtenrikstjenestemann"),
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER("loennKunTrygdeavgiftTilUtenlandskBorger"),
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER_SOM_GRENSEGJENGER("loennKunTrygdeavgiftTilUtenlandskBorgerSomGrensegjenger"),
    UFOERETRYGD_FRA_NAV("ufoeretrygdFraNAV"),
    UFOEREYTELSER_FRA_ANDRE("ufoereytelserFraAndre"),
    INTRODUKSJONSSTOENAD("introduksjonsstoenad"),
    ;

    companion object {
        fun fromValue(value: String): Trekkode = entries.find { it.value == value } ?: throw IllegalArgumentException("Ukjent trekkode $value")
    }
}
