package no.nav.sokos.skattekort.module.bestilling.svar

import java.math.BigDecimal

data class Root(
    val status: String,
    val arbeidsgiver: List<Arbeidsgiver>,
)

data class Arbeidsgiver(
    val arbeidsgiveridentifikator: Arbeidsgiveridentifikator,
    val arbeidstaker: List<Arbeidstaker>,
)

data class Arbeidsgiveridentifikator(
    val organisasjonsnummer: String,
)

data class Arbeidstaker(
    val arbeidstakeridentifikator: String,
    val resultatForSkattekort: String,
    val skattekort: Skattekort?,
    val inntektsaar: String,
)

data class Skattekort(
    val utstedtDato: String,
    val skattekortidentifikator: Long,
    val forskuddstrekk: List<Forskuddstrekk>,
)

data class Forskuddstrekk(
    val trekkode: String,
    val trekktabell: Trekktabell?,
    val trekkprosent: Trekkprosent?,
    val frikort: Frikort?,
)

data class Trekktabell(
    val tabellnummer: String,
    val prosentsats: Long,
    val antallMaanederForTrekk: BigDecimal,
)

data class Trekkprosent(
    val prosentsats: Long,
    val antallMaanederForTrekk: BigDecimal?,
)

data class Frikort(
    val frikortbeloep: BigDecimal? = null,
)
