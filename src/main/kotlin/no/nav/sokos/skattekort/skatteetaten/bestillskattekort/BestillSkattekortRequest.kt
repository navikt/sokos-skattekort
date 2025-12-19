package no.nav.sokos.skattekort.skatteetaten.bestillskattekort

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.module.person.Personidentifikator

// Data classes
@Serializable
data class BestillSkattekortRequest(
    val inntektsaar: String,
    val bestillingstype: String,
    val kontaktinformasjon: Kontaktinformasjon,
    val varslingstype: String? = null,
    val endringFraDato: LocalDate? = null,
    val forespoerselOmSkattekortTilArbeidsgiver: ForespoerselOmSkattekortTilArbeidsgiver? = null,
)

@Serializable
data class Kontaktinformasjon(
    val epostadresse: String,
    val mobiltelefonummer: String,
)

@Serializable
data class ForespoerselOmSkattekortTilArbeidsgiver(
    val arbeidsgiver: List<Arbeidsgiver>,
)

@Serializable
data class Arbeidsgiver(
    val arbeidsgiveridentifikator: ArbeidsgiverIdentifikator,
    val arbeidstakeridentifikator: List<String>,
)

@Serializable
data class ArbeidsgiverIdentifikator(
    val organisasjonsnummer: String,
)

private const val KONTAKT_EMAIL = "nav.ur.og.os@nav.no"

private const val KONTAKT_TLF = "+4721070000"

fun bestillSkattekortRequest(
    inntektsaar: Int,
    fnr: List<Personidentifikator>,
    bestillingOrgnr: String,
): BestillSkattekortRequest =
    BestillSkattekortRequest(
        inntektsaar = inntektsaar.toString(),
        bestillingstype = "HENT_ALLE_OPPGITTE",
        kontaktinformasjon =
            Kontaktinformasjon(
                epostadresse = KONTAKT_EMAIL,
                mobiltelefonummer = KONTAKT_TLF,
            ),
        varslingstype = "INGEN_VARSEL",
        forespoerselOmSkattekortTilArbeidsgiver =
            ForespoerselOmSkattekortTilArbeidsgiver(
                arbeidsgiver =
                    listOf(
                        Arbeidsgiver(
                            arbeidsgiveridentifikator = ArbeidsgiverIdentifikator(bestillingOrgnr),
                            arbeidstakeridentifikator = fnr.map { it.value },
                        ),
                    ),
            ),
    )

fun bestillOppdateringRequest(
    inntektsaar: Int,
    endringFraDato: LocalDate? = null,
): BestillSkattekortRequest =
    BestillSkattekortRequest(
        inntektsaar = inntektsaar.toString(),
        bestillingstype = "HENT_KUN_ENDRING",
        varslingstype = "INGEN_VARSEL",
        endringFraDato = endringFraDato,
        kontaktinformasjon =
            Kontaktinformasjon(
                epostadresse = KONTAKT_EMAIL,
                mobiltelefonummer = KONTAKT_TLF,
            ),
    )
