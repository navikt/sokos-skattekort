package no.nav.sokos.skattekort.kafka

import java.time.LocalDate
import javax.sql.DataSource

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.module.person.Foedselsnummer
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.pdl.PdlClientService
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private val logger = KotlinLogging.logger {}
const val FOLKEREGISTERIDENTIFIKATOR = "FOLKEREGISTERIDENTIFIKATOR_V1"

class IdentifikatorEndringService(
    private val dataSource: DataSource,
    private val pdlClientService: PdlClientService,
    private val personService: PersonService,
) {
    fun processIdentifikatorEndring(personHendelse: PersonHendelseDTO) {
        if (personHendelse.opplysningstype == FOLKEREGISTERIDENTIFIKATOR) {
            logger.info(marker = TEAM_LOGS_MARKER) { "Processing personHendelse: $personHendelse" }
            logger.info { "Behandle hendelse med hendelseId=${personHendelse.hendelseId}, opplysningstype=${personHendelse.opplysningstype} og endringstype=${personHendelse.endringstype.name}" }

            runCatching {
                when (personHendelse.endringstype) {
                    EndringstypeDTO.OPPRETTET, EndringstypeDTO.KORRIGERT -> {
                        if (personHendelse.folkeregisteridentifikator != null) {
                            behandleIdentifikator(personHendelse.folkeregisteridentifikator)
                        } else {
                            logger.error {
                                "Folkeregisteridentifikator er null for hendelseId=${personHendelse.hendelseId}. Kan ikke prosessere opprettet/korrigert hendelse uten folkeregisteridentifikator."
                            }
                        }
                    }

                    else ->
                        logger.info {
                            "Ingen prosessering av hendelse med hendelseId=${personHendelse.hendelseId}, opplysningstype=${personHendelse.opplysningstype} og endringstype=${personHendelse.endringstype.name}"
                        }
                }
            }.onFailure { exception ->
                logger.error(exception) {
                    "Feil ved prosessering av hendelse med hendelseId=${personHendelse.hendelseId}, opplysningstype=${personHendelse.opplysningstype} og endringstype=${personHendelse.endringstype.name}"
                }
            }
        }
    }

    private fun behandleIdentifikator(folkeregisteridentifikator: FolkeregisteridentifikatorDTO) {
        val identifikasjonsnummer = folkeregisteridentifikator.identifikasjonsnummer

        dataSource.transaction { tx ->
            if (PersonRepository.findPersonByFnr(tx, Personidentifikator(identifikasjonsnummer)) == null) {
                val pdlResponse = runBlocking { pdlClientService.getIdenterBolk(listOf(identifikasjonsnummer)) }
                val identList = pdlResponse[identifikasjonsnummer]!!.filter { it.historisk }.map { it.ident }

                if (identList.isNotEmpty()) {
                    val personId = personService.findPersonIdByPersonidentifikator(tx, identList)
                    personId?.let { id ->
                        logger.info(marker = TEAM_LOGS_MARKER) { "Oppdater personId=$id med folkeregisteridentifikator=$identifikasjonsnummer" }
                        personService.updateFoedselsnummer(
                            tx,
                            Foedselsnummer(
                                personId = id,
                                gjelderFom = LocalDate.now().toKotlinLocalDate(),
                                fnr = Personidentifikator(identifikasjonsnummer),
                            ),
                        )
                    } ?: logger.info(marker = TEAM_LOGS_MARKER) { "Ingen ident endringer med folkeregisteridentifikator=$identifikasjonsnummer" }
                }
            }
        }
    }
}
