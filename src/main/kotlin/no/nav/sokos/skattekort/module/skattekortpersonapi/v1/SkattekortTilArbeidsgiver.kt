package no.nav.sokos.skattekort.module.skattekortpersonapi.v1

import java.math.BigDecimal

import kotlinx.datetime.LocalDate

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class SkattekortTilArbeidsgiver(
    var navn: String? = null,
    val arbeidsgiver: List<Arbeidsgiver>,
)

data class Arbeidsgiver(
    val arbeidstaker: List<Arbeidstaker>,
    val arbeidsgiveridentifikator: IdentifikatorForEnhetEllerPerson,
)

data class Arbeidstaker(
    val inntektsaar: Long,
    val arbeidstakeridentifikator: String,
    val resultatPaaForespoersel: Resultatstatus,
    val skattekort: Skattekort? = null,
    val tilleggsopplysning: List<Tilleggsopplysning>? = null,
)

data class IdentifikatorForEnhetEllerPerson(
    val organisasjonsnummer: String? = null,
    val personidentifikator: String? = null,
)

enum class Resultatstatus(
    val value: String,
) {
    @JsonProperty("ikkeSkattekort")
    IKKE_SKATTEKORT("ikkeSkattekort"),

    @JsonProperty("vurderArbeidstillatelse")
    VURDER_ARBEIDSTILLATELSE("vurderArbeidstillatelse"),

    @JsonProperty("ikkeTrekkplikt")
    IKKE_TREKKPLIKT("ikkeTrekkplikt"),

    @JsonProperty("skattekortopplysningerOK")
    SKATTEKORTOPPLYSNINGER_OK("skattekortopplysningerOK"),

    @JsonProperty("ugyldigOrganisasjonsnummer")
    UGYLDIG_ORGANISASJONSNUMMER("ugyldigOrganisasjonsnummer"),

    @JsonProperty("ugyldigFoedselsEllerDnummer")
    UGYLDIG_FOEDSELS_ELLER_DNUMMER("ugyldigFoedselsEllerDnummer"),

    @JsonProperty("utgaattDnummerSkattekortForFoedselsnummerErLevert")
    UTGAATT_DNUMMER_SKATTEKORT_FOR_FOEDSELSNUMMER_ER_LEVERT("utgaattDnummerSkattekortForFoedselsnummerErLevert"),
}

enum class Tilleggsopplysning(
    val value: String,
) {
    @JsonProperty("oppholdPaaSvalbard")
    OPPHOLD_PAA_SVALBARD("oppholdPaaSvalbard"),

    @JsonProperty("kildeskattpensjonist")
    KILDESKATTPENSJONIST("kildeskattpensjonist"),

    @JsonProperty("oppholdITiltakssone")
    OPPHOLD_I_TILTAKSSONE("oppholdITiltakssone"),

    @JsonProperty("kildeskattPaaLoenn")
    KILDESKATT_PAA_LOENN("kildeskattPaaLoenn"),
}

data class Skattekort(
    val utstedtDato: LocalDate,
    val skattekortidentifikator: Long,
    val forskuddstrekk: List<Forskuddstrekk>? = null,
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    // Type blir med i JSON responsen som forteller hvilken type Forskuddstrekk dette er
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Frikort::class, name = "Frikort"),
    JsonSubTypes.Type(value = Trekktabell::class, name = "Trekktabell"),
    JsonSubTypes.Type(value = Trekkprosent::class, name = "Trekkprosent"),
)
interface Forskuddstrekk {
    val trekkode: Trekkode
}

data class Frikort(
    override val trekkode: Trekkode,
    val frikortbeloep: BigDecimal? = null,
) : Forskuddstrekk

enum class Tabelltype(
    val value: String,
) {
    @JsonProperty("trekktabellForPensjon")
    TREKKTABELL_FOR_PENSJON("trekktabellForPensjon"),

    @JsonProperty("trekktabellForLoenn")
    TREKKTABELL_FOR_LOENN("trekktabellForLoenn"),
}

data class Trekktabell(
    override val trekkode: Trekkode,
    val tabelltype: Tabelltype? = null,
    val tabellnummer: String? = null,
    val prosentsats: BigDecimal? = null,
    val antallMaanederForTrekk: BigDecimal? = null,
) : Forskuddstrekk

data class Trekkprosent(
    override val trekkode: Trekkode,
    val prosentsats: BigDecimal? = null,
    var antallMaanederForTrekk: BigDecimal? = null,
) : Forskuddstrekk

enum class Trekkode(
    val value: String,
) {
    @JsonProperty("loennFraHovedarbeidsgiver")
    LOENN_FRA_HOVEDARBEIDSGIVER("loennFraHovedarbeidsgiver"),

    @JsonProperty("loennFraBiarbeidsgiver")
    LOENN_FRA_BIARBEIDSGIVER("loennFraBiarbeidsgiver"),

    @JsonProperty("loennFraNAV")
    LOENN_FRA_NAV("loennFraNAV"),

    @JsonProperty("pensjon")
    PENSJON("pensjon"),

    @JsonProperty("pensjonFraNAV")
    PENSJON_FRA_NAV("pensjonFraNAV"),

    @JsonProperty("loennTilUtenrikstjenestemann")
    LOENN_TIL_UTENRIKSTJENESTEMANN("loennTilUtenrikstjenestemann"),

    @JsonProperty("loennKunTrygdeavgiftTilUtenlandskBorger")
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER("loennKunTrygdeavgiftTilUtenlandskBorger"),

    @JsonProperty("loennKunTrygdeavgiftTilUtenlandskBorgerSomGrensegjenger")
    LOENN_KUN_TRYGDEAVGIFT_TIL_UTENLANDSK_BORGER_SOM_GRENSEGJENGER("loennKunTrygdeavgiftTilUtenlandskBorgerSomGrensegjenger"),

    @JsonProperty("ufoeretrygdFraNAV")
    UFOERETRYGD_FRA_NAV("ufoeretrygdFraNAV"),

    @JsonProperty("ufoereytelserFraAndre")
    UFOEREYTELSER_FRA_ANDRE("ufoereytelserFraAndre"),

    @JsonProperty("introduksjonsstoenad")
    INTRODUKSJONSSTOENAD("introduksjonsstoenad"),
}
