package no.nav.sokos.skattekort.module.skattekort

import javax.sql.DataSource

import mu.KotlinLogging

import no.nav.sokos.skattekort.api.skattekortperson.SkattekortPersonDto
import no.nav.sokos.skattekort.api.skattekortperson.toDto
import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.exception.PersonNotFoundException
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}

class SkattekortPersonService(
    val dataSource: DataSource,
) {
    fun hentSkattekortPerson(skattekortPersonRequest: SkattekortPersonRequest): List<SkattekortPersonDto> =
        dataSource.transaction { tx ->
            require(skattekortPersonRequest.fnr.all { it.isDigit() }) { "fnr kan bare inneholde siffer, var ${skattekortPersonRequest.fnr}" }
            require(skattekortPersonRequest.fnr.length == 11) { "fnr må ha lengde 11, var ${skattekortPersonRequest.fnr}" }
            require(
                skattekortPersonRequest.inntektsaar in 2025..<2100,
            ) { "inntektsaar ser ikke ut som et gyldig årstall, var ${skattekortPersonRequest.inntektsaar}" }

            // kan vi logge dette? fnr=${skattekortPersonRequest.fnr} inntektsaar=${skattekortPersonRequest.inntektsaar}
            logger.info { "Henter skattekort" }

            val person =
                PersonRepository.findPersonByFnr(tx, Personidentifikator(skattekortPersonRequest.fnr))
                    ?: throw PersonNotFoundException("Fant ikke person med fnr ${skattekortPersonRequest.fnr}")

            val skattekort: List<Skattekort> =
                SkattekortRepository
                    .findAllByPersonId(
                        tx,
                        person.id!!,
                        skattekortPersonRequest.inntektsaar.toInt(),
                        adminRole = false,
                    )

            val dtoList = skattekort.map { it.toDto(skattekortPersonRequest.fnr) }

            // kan vi logge dette? fnr=${skattekortPersonRequest.fnr} inntektsaar=${skattekortPersonRequest.inntektsaar}
            logger.info { "Fant ${dtoList.size} skattekort" }
            dtoList
        }
}
