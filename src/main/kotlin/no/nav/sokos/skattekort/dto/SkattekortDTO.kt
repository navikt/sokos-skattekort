package no.nav.sokos.skattekort.dto

import java.math.BigDecimal

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.skattekort.Forskuddstrekk
import no.nav.sokos.skattekort.skattekort.Frikort
import no.nav.sokos.skattekort.skattekort.Prosentkort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.skattekort.Tabellkort
import no.nav.sokos.skattekort.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.skattekort.Trekkode

@Serializable
data class SkattekortDTO(
    val utstedtDato: LocalDate?,
    val inntektsaar: Int,
    val resultatForSkattekort: String?,
    val forskuddstrekkList: List<ForskuddstrekkDTO>,
    val tilleggsopplysningList: List<String> = emptyList(),
) {
    constructor(skattekort: Skattekort) : this(
        utstedtDato = skattekort.utstedtDato,
        inntektsaar = skattekort.inntektsaar,
        resultatForSkattekort = skattekort.resultatForSkattekort.value,
        forskuddstrekkList =
            skattekort.forskuddstrekkList.map {
                when (it) {
                    is Frikort -> {
                        ForskuddstrekkDTO(
                            trekkode = it.trekkode.value,
                            frikort = FrikortDTO(frikortBeloep = it.frikortBeloep),
                        )
                    }

                    is Prosentkort -> {
                        ForskuddstrekkDTO(
                            trekkode = it.trekkode.value,
                            prosentkort =
                                ProsentkortDTO(
                                    prosentSats = it.prosentSats.toDouble(),
                                    antallMndForTrekk = it.antallMndForTrekk?.toDouble(),
                                ),
                        )
                    }

                    is Tabellkort -> {
                        ForskuddstrekkDTO(
                            trekkode = it.trekkode.value,
                            trekktabell =
                                TabellkortDTO(
                                    tabell = it.tabellNummer,
                                    prosentSats = it.prosentSats.toDouble(),
                                    antallMndForTrekk = it.antallMndForTrekk.toDouble(),
                                ),
                        )
                    }
                }
            },
        tilleggsopplysningList = skattekort.tilleggsopplysningList.map { it.value },
    )

    fun toDomainSkattekort(
        personId: PersonId,
        utstedtDato: LocalDate,
        identifikator: String?,
        kilde: SkattekortKilde,
    ): Skattekort =
        Skattekort(
            personId = personId,
            utstedtDato = utstedtDato,
            identifikator = identifikator,
            kilde = kilde.value,
            inntektsaar = this.inntektsaar,
            resultatForSkattekort = resultatForSkattekort?.let(ResultatForSkattekort::fromValue) ?: ResultatForSkattekort.SkattekortopplysningerOK,
            forskuddstrekkList = this.forskuddstrekkList.map { it.toDomainForskuddstrekk() },
            tilleggsopplysningList = this.tilleggsopplysningList.map { Tilleggsopplysning.fromValue(it) },
        )
}

@Serializable
data class ForskuddstrekkDTO(
    val trekkode: String,
    val frikort: FrikortDTO? = null,
    val prosentkort: ProsentkortDTO? = null,
    val trekktabell: TabellkortDTO? = null,
) {
    fun toDomainForskuddstrekk(): Forskuddstrekk =
        when {
            frikort != null -> {
                Frikort(
                    trekkode = Trekkode.fromValue(trekkode),
                    frikortBeloep = frikort.frikortBeloep,
                )
            }

            trekktabell != null -> {
                Tabellkort(
                    trekkode = Trekkode.fromValue(trekkode),
                    tabellNummer = trekktabell.tabell,
                    prosentSats = BigDecimal.valueOf(trekktabell.prosentSats),
                    antallMndForTrekk = BigDecimal.valueOf(trekktabell.antallMndForTrekk),
                )
            }

            prosentkort != null -> {
                Prosentkort(
                    trekkode = Trekkode.fromValue(trekkode),
                    prosentSats = BigDecimal.valueOf(prosentkort.prosentSats),
                    antallMndForTrekk =
                        prosentkort.antallMndForTrekk?.let { antallMnd ->
                            BigDecimal.valueOf(antallMnd)
                        },
                )
            }

            else -> {
                error("Ugyldig Forskuddstrekk: $this")
            }
        }
}

@Serializable
data class FrikortDTO(
    val frikortBeloep: Int? = null,
)

@Serializable
data class ProsentkortDTO(
    val prosentSats: Double,
    val antallMndForTrekk: Double? = null,
)

@Serializable
data class TabellkortDTO(
    val tabell: String,
    val prosentSats: Double,
    val antallMndForTrekk: Double,
)

val validTrekkodeList =
    listOf(
        Trekkode.LOENN_FRA_NAV,
        Trekkode.PENSJON_FRA_NAV,
        Trekkode.UFOERETRYGD_FRA_NAV,
    )

val validTilleggsopplysningList =
    listOf(
        Tilleggsopplysning.OPPHOLD_PAA_SVALBARD.value,
        Tilleggsopplysning.KILDESKATT_PAA_PENSJON.value,
        Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE.value,
    )
