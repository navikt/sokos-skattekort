package no.nav.sokos.skattekort.api.model.v2

import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.api.model.v1.ForskuddstrekkDTO
import no.nav.sokos.skattekort.api.model.v1.FrikortDTO
import no.nav.sokos.skattekort.api.model.v1.ProsentkortDTO
import no.nav.sokos.skattekort.api.model.v1.TabellkortDTO
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.Frikort
import no.nav.sokos.skattekort.skattekort.Prosentkort
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortKilde
import no.nav.sokos.skattekort.skattekort.Tabellkort

@Serializable
data class SkattekortDTO(
    val fnr: String? = null,
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
    constructor(skattekort: Skattekort, personidentifikator: Personidentifikator?) : this(
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
        fnr = personidentifikator?.value,
        id = skattekort.id?.value ?: throw IllegalStateException("Skattekort fra databasen mangler id"),
        identifikator = skattekort.identifikator,
        inntektsaar = skattekort.inntektsaar,
        kilde = SkattekortKilde.fromValue(skattekort.kilde),
        opprettet = skattekort.opprettet.toKotlinInstant(),
        resultatForSkattekort = skattekort.resultatForSkattekort.value,
        tilleggsopplysningList = skattekort.tilleggsopplysningList.map { it.value },
        utstedtDato = skattekort.utstedtDato?.toKotlinLocalDate(),
    )

    constructor(skattekort: Skattekort) : this(skattekort, null)
}
