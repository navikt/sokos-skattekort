package no.nav.sokos.skattekort.dto.v2

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.dto.ForskuddstrekkDTO
import no.nav.sokos.skattekort.dto.FrikortDTO
import no.nav.sokos.skattekort.dto.ProsentkortDTO
import no.nav.sokos.skattekort.dto.TabellkortDTO
import no.nav.sokos.skattekort.skattekort.Frikort
import no.nav.sokos.skattekort.skattekort.Prosentkort
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.skattekort.Tabellkort

@Serializable
data class SkattekortDTO(
    val forskuddstrekkList: List<ForskuddstrekkDTO>,
    val id: Long,
    val identifikator: String?,
    val inntektsaar: Int,
    val kilde: SkattekortKilde,
    val opprettet: Instant,
    val resultatForSkattekort: String?,
    val tilleggsopplysningList: List<String> = emptyList(),
    val utstedtDato: LocalDate?,
) {
    constructor(skattekort: Skattekort) : this(
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
        id = skattekort.id?.value ?: throw IllegalStateException("Skattekort fra databasen mangler id"),
        identifikator = skattekort.identifikator,
        inntektsaar = skattekort.inntektsaar,
        kilde = SkattekortKilde.fromValue(skattekort.kilde),
        opprettet = skattekort.opprettet,
        resultatForSkattekort = skattekort.resultatForSkattekort.value,
        tilleggsopplysningList = skattekort.tilleggsopplysningList.map { it.value },
        utstedtDato = skattekort.utstedtDato,
    )
}
