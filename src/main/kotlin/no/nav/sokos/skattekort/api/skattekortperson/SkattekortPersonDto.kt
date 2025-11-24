package no.nav.sokos.skattekort.api.skattekortperson

import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.BigDecimalJson
import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk
import no.nav.sokos.skattekort.module.skattekort.Frikort
import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.Skattekort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort

@Serializable
data class SkattekortPersonDto(
    val inntektsaar: Long,
    val arbeidstakeridentifikator: String,
    val resultatPaaForespoersel: String,
    val skattekort: SkattekortDto? = null,
    val tilleggsopplysning: List<String> = emptyList(),
)

@Serializable
data class SkattekortDto(
    val utstedtDato: String?,
    val skattekortidentifikator: Long?,
    val forskuddstrekk: List<ForskuddstrekkDto>,
)

@Serializable
data class ForskuddstrekkDto(
    val type: String,
    val trekkode: String,
    val prosentsats: BigDecimalJson? = null,
    val antallMaanederForTrekk: BigDecimalJson? = null,
    val frikortbeloep: Int? = null,
    val tabellnummer: String? = null,
)

fun Skattekort.toDto(fnr: String): SkattekortPersonDto =
    SkattekortPersonDto(
        inntektsaar = inntektsaar.toLong(),
        arbeidstakeridentifikator = fnr,
        resultatPaaForespoersel = resultatForSkattekort.value,
        skattekort =
            if (forskuddstrekkList.isNotEmpty()) {
                SkattekortDto(
                    utstedtDato = utstedtDato?.toString(),
                    skattekortidentifikator = identifikator?.toLongOrNull(),
                    forskuddstrekk = forskuddstrekkList.map { it.toDto() },
                )
            } else {
                null
            },
        tilleggsopplysning = tilleggsopplysningList.map { it.value },
    )

private fun Forskuddstrekk.toDto(): ForskuddstrekkDto =
    when (this) {
        is Frikort ->
            ForskuddstrekkDto(
                type = "Frikort",
                trekkode = trekkode.name,
                frikortbeloep = frikortBeloep,
            )

        is Prosentkort ->
            ForskuddstrekkDto(
                type = "Trekkprosent",
                trekkode = trekkode.name,
                prosentsats = this.prosentSats,
                antallMaanederForTrekk = this.antallMndForTrekk,
            )

        is Tabellkort ->
            ForskuddstrekkDto(
                type = "Trekktabell",
                trekkode = trekkode.name,
                prosentsats = this.prosentSats,
                antallMaanederForTrekk = this.antallMndForTrekk,
                tabellnummer = this.tabellNummer,
            )
    }
