package no.nav.sokos.skattekort.person.kafka

import java.time.Instant

import kotlinx.coroutines.launch

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.apache.kafka.clients.producer.ProducerRecord

import no.nav.person.pdl.leesah.Endringstype
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.person.pdl.leesah.folkeregisteridentifikator.Folkeregisteridentifikator
import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.listener.KafkaListener
import no.nav.sokos.skattekort.listener.KafkaListener.kafkaProducer
import no.nav.sokos.skattekort.util.BackgroundTaskRunner
import no.nav.sokos.skattekort.utils.TestUtils.eventuallyConfiguration

class KafkaConsumerServiceTest :
    BehaviorSpec({
        extensions(KafkaListener)

        val topic = "pdl.leesah-v1-test"

        val kafkaConfig by lazy {
            KafkaListener.createKafkaTopic(topic)
            KafkaListener.getKafkaConfig(topic)
        }

        val identifikatorEndringService = mockk<IdentifikatorEndringService>()
        coEvery { identifikatorEndringService.processIdentifikatorEndring(any()) } returns Unit

        val kafkaConsumerService by lazy {
            KafkaConsumerService(kafkaConfig, identifikatorEndringService)
        }

        Given("en kjørende KafkaConsumerService som lytter på personhendelse-topicet") {
            val applicationState = ApplicationState(readyInit = true)
            val backgroundTaskRunner = BackgroundTaskRunner(applicationState)
            val consumerJob = backgroundTaskRunner.launch { kafkaConsumerService.start(applicationState) }

            try {
                When("det publiseres en Personhendelse med opprettet folkeregisteridentifikator på topicet") {
                    kafkaProducer.send(ProducerRecord(topic, null, personHendelse())).get()

                    Then("skal hendelsen konsumeres og identifikatorendringen prosesseres") {
                        eventually(eventuallyConfiguration) {
                            coVerify(exactly = 1) {
                                identifikatorEndringService.processIdentifikatorEndring(
                                    PersonHendelseDTO(
                                        hendelseId = "aba9b36f-43cd-4b5b-b4e8-f991af902bbe",
                                        personidenter = listOf("24519539620", "2294850419623"),
                                        opplysningstype = FOLKEREGISTERIDENTIFIKATOR,
                                        endringstype = EndringstypeDTO.OPPRETTET,
                                        folkeregisteridentifikator =
                                            FolkeregisteridentifikatorDTO(
                                                identifikasjonsnummer = "24519539620",
                                                type = "FNR",
                                                status = "iBruk",
                                            ),
                                    ),
                                )
                            }
                        }
                    }
                }
            } finally {
                applicationState.ready = false
                kafkaConsumerService.close()
                consumerJob.join()
            }
        }
    })

private fun personHendelse() =
    Personhendelse
        .newBuilder()
        .setHendelseId("aba9b36f-43cd-4b5b-b4e8-f991af902bbe")
        .setPersonidenter(listOf("24519539620", "2294850419623"))
        .setMaster("FREG")
        .setOpprettet(Instant.now())
        .setOpplysningstype(FOLKEREGISTERIDENTIFIKATOR)
        .setEndringstype(Endringstype.OPPRETTET)
        .setFolkeregisteridentifikator(Folkeregisteridentifikator("24519539620", "FNR", "iBruk"))
        .build()
