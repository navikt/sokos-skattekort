package no.nav.sokos.skattekort.api.skattekortpersonapi.v1

import java.math.BigDecimal

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.BigDecimalJson
import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort

@Serializable
data class Arbeidstaker(
    val inntektsaar: Long,
    val arbeidstakeridentifikator: String,
    val resultatPaaForespoersel: String,
    val skattekort: Skattekort? = null,
    val tilleggsopplysning: List<String>? = null,
) {
    constructor(inntektsaar: Long, fnr: String, sk: no.nav.sokos.skattekort.module.skattekort.Skattekort) : this(
        inntektsaar = inntektsaar,
        arbeidstakeridentifikator = fnr,
        resultatPaaForespoersel = sk.resultatForSkattekort.value,
        skattekort =
            Skattekort(
                utstedtDato = sk.utstedtDato,
                skattekortidentifikator = sk.identifikator?.toLong(),
                forskuddstrekk = sk.forskuddstrekkList.map { Forskuddstrekk.fromDomainModel(it) },
            ),
        tilleggsopplysning = sk.tilleggsopplysningList.map { it.value },
    )
}

@Serializable
data class IdentifikatorForEnhetEllerPerson(
    val organisasjonsnummer: String? = null,
    val personidentifikator: String? = null,
)

@Serializable
data class Skattekort(
    val utstedtDato: LocalDate? = null,
    val skattekortidentifikator: Long? = null,
    val forskuddstrekk: List<Forskuddstrekk>? = null,
)

@Serializable
@SerialName("forskuddstrekk")
sealed interface Forskuddstrekk {
    val trekkode: String

    companion object {
        fun fromDomainModel(forskuddstrekk: no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk): Forskuddstrekk =
            when (forskuddstrekk) {
                is no.nav.sokos.skattekort.module.skattekort.Frikort ->
                    Frikort(
                        forskuddstrekk.trekkode.value,
                        forskuddstrekk.frikortBeloep?.let { BigDecimal(it) },
                    )

                is Tabellkort ->
                    Trekktabell(
                        trekkode = forskuddstrekk.trekkode.value,
                        tabellnummer = forskuddstrekk.tabellNummer,
                        prosentsats = forskuddstrekk.prosentSats,
                        antallMaanederForTrekk = forskuddstrekk.antallMndForTrekk,
                    )

                is Prosentkort ->
                    Trekkprosent(
                        trekkode = forskuddstrekk.trekkode.value,
                        prosentsats = forskuddstrekk.prosentSats,
                        antallMaanederForTrekk = forskuddstrekk.antallMndForTrekk,
                    )
            }
    }
}

@Serializable
@SerialName("Frikort")
data class Frikort(
    override val trekkode: String,
    val frikortbeloep: BigDecimalJson? = null,
) : Forskuddstrekk

@Serializable
@SerialName("Trekktabell")
data class Trekktabell(
    override val trekkode: String,
    // val tabelltype: Tabelltype? = null, Returneres ikke lenger av skatt
    val tabellnummer: String? = null,
    val prosentsats: BigDecimalJson? = null,
    val antallMaanederForTrekk: BigDecimalJson? = null,
) : Forskuddstrekk

@Serializable
@SerialName("Trekkprosent")
data class Trekkprosent(
    override val trekkode: String,
    val prosentsats: BigDecimalJson? = null,
    var antallMaanederForTrekk: BigDecimalJson? = null,
) : Forskuddstrekk
