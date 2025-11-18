package no.nav.sokos.skattekort.skatteetaten.bestillskattekort

import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.module.person.Personidentifikator

// Data classes
@Serializable
data class BestillSkattekortRequest(
    val inntektsaar: String,
    val bestillingstype: String,
    val kontaktinformasjon: Kontaktinformasjon,
    val varslingstype: String? = null,
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

fun bestillSkattekortRequest(
    inntektsaar: Int,
    fnr: List<Personidentifikator>,
): BestillSkattekortRequest =
    BestillSkattekortRequest(
        inntektsaar = inntektsaar.toString(),
        bestillingstype = "HENT_ALLE_OPPGITTE",
        kontaktinformasjon =
            Kontaktinformasjon(
                epostadresse = "john.smith@example.com",
                mobiltelefonummer = "+4794123456",
            ),
        varslingstype = "VARSEL_VED_FOERSTE_ENDRING",
        forespoerselOmSkattekortTilArbeidsgiver =
            ForespoerselOmSkattekortTilArbeidsgiver(
                arbeidsgiver =
                    listOf(
                        Arbeidsgiver(
                            arbeidsgiveridentifikator = ArbeidsgiverIdentifikator("312978083"),
                            arbeidstakeridentifikator = fnr.map { it.value },
                        ),
                    ),
            ),
    )

fun bestillOppdateringRequest(inntektsaar: Int): BestillSkattekortRequest =
    BestillSkattekortRequest(
        inntektsaar = inntektsaar.toString(),
        bestillingstype = "HENT_KUN_ENDRING",
        varslingstype = "VARSEL_VED_FOERSTE_ENDRING",
        kontaktinformasjon =
            Kontaktinformasjon(
                epostadresse = "john.smith@example.com",
                mobiltelefonummer = "+4794123456",
            ),
    )
