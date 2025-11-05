package no.nav.sokos.skattekort.skatteetaten.hentskattekort

import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.BigDecimalJson

@Serializable
data class HentSkattekortResponse(
    val status: String,
    val arbeidsgiver: List<Arbeidsgiver>? = emptyList(),
)

@Serializable
data class Arbeidsgiver(
    val arbeidsgiveridentifikator: Arbeidsgiveridentifikator,
    val arbeidstaker: List<Arbeidstaker>,
)

@Serializable
data class Arbeidsgiveridentifikator(
    val organisasjonsnummer: String,
)

@Serializable
data class Arbeidstaker(
    val arbeidstakeridentifikator: String,
    val resultatForSkattekort: String,
    val skattekort: Skattekort? = null,
    val tilleggsopplysning: List<String>? = null,
    val inntektsaar: String,
)

@Serializable
data class Skattekort(
    val utstedtDato: String,
    val skattekortidentifikator: Long,
    val forskuddstrekk: List<Forskuddstrekk>,
)

@Serializable
data class Forskuddstrekk(
    val trekkode: String,
    val trekktabell: Trekktabell? = null,
    val trekkprosent: Trekkprosent? = null,
    val frikort: Frikort? = null,
)

@Serializable
data class Trekktabell(
    val tabellnummer: String,
    val prosentsats: BigDecimalJson,
    val antallMaanederForTrekk: BigDecimalJson,
)

@Serializable
data class Trekkprosent(
    val prosentsats: BigDecimalJson,
    val antallMaanederForTrekk: BigDecimalJson? = null,
)

@Serializable
data class Frikort(
    val frikortbeloep: BigDecimalJson? = null,
)
