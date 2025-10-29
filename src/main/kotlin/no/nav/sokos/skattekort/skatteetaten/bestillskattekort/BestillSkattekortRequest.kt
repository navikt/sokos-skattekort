package no.nav.sokos.skattekort.skatteetaten.bestillskattekort

import kotlinx.serialization.Serializable

// Data classes
@Serializable
data class BestillSkattekortRequest(
    val inntektsaar: String,
    val bestillingstype: String,
    val kontaktinformasjon: Kontaktinformasjon,
    val varslingstype: String,
    val forespoerselOmSkattekortTilArbeidsgiver: ForespoerselOmSkattekortTilArbeidsgiver,
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
