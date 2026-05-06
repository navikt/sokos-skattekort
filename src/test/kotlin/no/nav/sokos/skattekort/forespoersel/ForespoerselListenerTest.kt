package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDateTime

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.TestUtils.eventuallyConfiguration
import no.nav.sokos.skattekort.utils.createTestHttpClient

class ForespoerselListenerTest :
    BehaviorSpec({
        extensions(listOf(MQListener, DbListener, WiremockListener))

        val forSystemQueue = ActiveMQQueue("FOR_SYSTEM")
        val forSystemBOQQueue = ActiveMQQueue("FOR_SYSTEM_BOQ")

        val pdlClientService: PdlClientService by lazy {
            PdlClientService(
                httpClient = createTestHttpClient(),
                pdlUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        val forespoerselListener: ForespoerselListener by lazy {
            ForespoerselListener(
                connectionFactory = MQListener.connectionFactory,
                forespoerselService =
                    ForespoerselService(
                        dataSource = DbListener.dataSource,
                        personService = PersonService(DbListener.dataSource, pdlClientService),
                        featureToggles = UnleashIntegration(),
                    ),
                forespoerselQueue = forSystemQueue,
                forespoerselBoqQueue = forSystemBOQQueue,
            )
        }

        afterEach {
            // Ensure listener is stopped after each test
            forespoerselListener.onOppdateringChanged(false)
        }

        Given("listeneren er konfigurert for køene FOR_SYSTEM og FOR_SYSTEM_BOQ") {
            When("oppdatering slås på") {
                val fnr = "11111111111"
                val jmsMessage = "OS;2025;$fnr"

                Then("skal JMS context og consumer opprettes og meldingen behandles") {
                    // Må ha withConstantNow pga. hvis denne testen kjører fra 15.12 til 31.12, så vil det bli 2 bestillinger
                    withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                        WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk(fnr))

                        forespoerselListener.onOppdateringChanged(true)

                        JmsTestUtil.sendMessage(msg = jmsMessage, queue = forSystemQueue)

                        eventually(eventuallyConfiguration) {
                            DbListener.dataSource.transaction { session ->
                                ForespoerselRepository.getAllForespoersel(session) shouldNotBeNull {
                                    size shouldBe 1
                                    first().dataMottatt shouldBe jmsMessage
                                }
                            }
                        }
                    }
                }
            }

            When("listeneren startes og deretter stoppes") {
                val jmsMessage = "OS;2025;22222222222"

                Then("skal JMS consumer og context lukkes slik at nye meldinger blir liggende i kø") {
                    forespoerselListener.onOppdateringChanged(true)
                    forespoerselListener.onOppdateringChanged(false)

                    JmsTestUtil.assertAllQueuesAreEmpty()

                    // Verifiser at listener ikke mottar meldinger etter stop
                    JmsTestUtil.sendMessage(msg = jmsMessage, queue = forSystemQueue)

                    eventually(eventuallyConfiguration) {
                        DbListener.dataSource.transaction { session ->
                            ForespoerselRepository.getAllForespoersel(session) shouldBe emptyList()
                        }
                    }
                    JmsTestUtil.getMessages(forSystemQueue).size shouldBe 1
                }
            }
        }

        Given("listeneren allerede kjører") {
            When("oppdatering slås på en gang til") {
                val fnr = "55555555555"
                val jmsMessage = "OS;2025;$fnr"

                Then("skal det ikke opprettes en ny listener og meldinger skal fortsatt behandles") {
                    // Må ha withConstantNow pga. hvis denne testen kjører fra 15.12 til 31.12, så vil det bli 2 bestillinger
                    withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                        WiremockListener.wiremockPDLStub(WiremockListener.generateHentIdenterBolk(fnr))

                        forespoerselListener.onOppdateringChanged(enabled = true)

                        forespoerselListener.onOppdateringChanged(enabled = true)

                        // Listener skal fortsatt være kjørende og mottar meldinger
                        JmsTestUtil.sendMessage(msg = jmsMessage, queue = forSystemQueue)

                        eventually(eventuallyConfiguration) {
                            DbListener.dataSource.transaction { session ->
                                ForespoerselRepository.getAllForespoersel(session).any {
                                    it.dataMottatt == jmsMessage
                                } shouldBe true
                            }
                        }
                    }
                }
            }
        }

        Given("listeneren allerede er stoppet") {
            When("oppdatering slås av en gang til") {
                val jmsMessage = "OS;2025;66666666666"

                Then("skal det ikke skje noe og meldingen skal bli liggende i kø") {
                    forespoerselListener.onOppdateringChanged(enabled = false)

                    forespoerselListener.onOppdateringChanged(enabled = false)

                    JmsTestUtil.sendMessage(msg = jmsMessage, queue = forSystemQueue)

                    eventually(eventuallyConfiguration) {
                        DbListener.dataSource.transaction { session ->
                            ForespoerselRepository.getAllForespoersel(session) shouldBe emptyList()
                        }
                    }
                    JmsTestUtil.getMessages(forSystemQueue).size shouldBe 1
                }
            }
        }
    })
