package no.nav.sokos.skattekort.module.skattekort

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Arbeidstaker
import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class SkattekortPersonService(
    val dataSource: DataSource,
) {
    fun hentSkattekortPerson(skattekortPersonRequest: SkattekortPersonRequest): List<Arbeidstaker> =
        dataSource.transaction { tx ->
            require(skattekortPersonRequest.fnr.all { it.isDigit() }, { "fnr kan bare inneholde siffer, var ${skattekortPersonRequest.fnr}" })
            require(skattekortPersonRequest.fnr.length == 11, { "fnr må ha lengde 11, var ${skattekortPersonRequest.fnr}" })
            require(
                skattekortPersonRequest.inntektsaar < 2100 && skattekortPersonRequest.inntektsaar > 2024,
                { "inntektsaar ser ikke ut som et gyldig årstall, var ${skattekortPersonRequest.inntektsaar}" },
            )
            val person =
                PersonRepository.findPersonByFnr(tx, Personidentifikator(skattekortPersonRequest.fnr))
                    ?: throw IllegalArgumentException("Fant ikke person med fnr ${skattekortPersonRequest.fnr}")
            val skattekort: List<Skattekort> =
                SkattekortRepository
                    .findAllByPersonId(
                        tx,
                        person.id!!,
                        skattekortPersonRequest.inntektsaar.toInt(),
                    )
            skattekort.map {
                Arbeidstaker(
                    skattekortPersonRequest.inntektsaar.toLong(),
                    skattekortPersonRequest.fnr,
                    it,
                )
            }
        }
}
