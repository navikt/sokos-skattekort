package no.nav.sokos.skattekort.dto

import java.math.BigDecimal

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.skattekort.Frikort
import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.Skattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.module.skattekort.Tabellkort
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.module.skattekort.Trekkode

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
                            frikortBeloep = it.frikortBeloep,
                        )
                    }

                    is Prosentkort -> {
                        ForskuddstrekkDTO(
                            trekkode = it.trekkode.value,
                            prosentSats = it.prosentSats.toDouble(),
                            antallMndForTrekk = it.antallMndForTrekk?.toDouble(),
                        )
                    }

                    is Tabellkort -> {
                        ForskuddstrekkDTO(
                            trekkode = it.trekkode.value,
                            tabell = it.tabellNummer,
                            prosentSats = it.prosentSats.toDouble(),
                            antallMndForTrekk = it.antallMndForTrekk.toDouble(),
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
            forskuddstrekkList =
                this.forskuddstrekkList.map {
                    when {
                        it.frikortBeloep != null -> {
                            Frikort(
                                trekkode = Trekkode.fromValue(it.trekkode),
                                frikortBeloep = it.frikortBeloep,
                            )
                        }

                        it.tabell != null && it.prosentSats != null && it.antallMndForTrekk != null -> {
                            Tabellkort(
                                trekkode = Trekkode.fromValue(it.trekkode),
                                tabellNummer = it.tabell,
                                prosentSats = BigDecimal.valueOf(it.prosentSats),
                                antallMndForTrekk = BigDecimal.valueOf(it.antallMndForTrekk),
                            )
                        }

                        it.prosentSats != null -> {
                            Prosentkort(
                                trekkode = Trekkode.fromValue(it.trekkode),
                                prosentSats = BigDecimal.valueOf(it.prosentSats),
                                antallMndForTrekk =
                                    it.antallMndForTrekk?.let { antallMnd ->
                                        BigDecimal.valueOf(antallMnd)
                                    },
                            )
                        }

                        else -> {
                            error("Ugyldig Forskuddstrekk: $it")
                        }
                    }
                },
            tilleggsopplysningList = this.tilleggsopplysningList.map { Tilleggsopplysning.fromValue(it) },
        )
}

@Serializable
data class ForskuddstrekkDTO(
    val trekkode: String,
    val frikortBeloep: Int? = null,
    val tabell: String? = null,
    val prosentSats: Double? = null,
    val antallMndForTrekk: Double? = null,
)
