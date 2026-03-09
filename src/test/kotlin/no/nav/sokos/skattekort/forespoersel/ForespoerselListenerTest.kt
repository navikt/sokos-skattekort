package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDateTime

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.TestUtils.eventuallyConfiguration

class ForespoerselListenerTest :
    FunSpec({
        extensions(listOf(MQListener, DbListener, WiremockListener))

        val forSystemQueue = ActiveMQQueue("FOR_SYSTEM")
        val forSystemBOQQueue = ActiveMQQueue("FOR_SYSTEM_BOQ")

        val pdlClientService: PdlClientService by lazy {
            PdlClientService(
                httpClient = createHttpClient(),
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
            forespoerselListener.stop()
        }

        test("start() skal opprette JMS context og consumer") {
            // Må ha withConstantNow pga. hvis denne testen kjører fra 15.12 til 31.12, så vil det bli 2 bestillinger
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                val fnr = "11111111111"
                WiremockListener.wiremockPDLStub(WiremockListener.generatePDLResponse(fnr))

                forespoerselListener.start()

                val jmsMessage = "OS;2025;$fnr"
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

        test("stop() skal lukke JMS consumer og context") {
            forespoerselListener.start()
            forespoerselListener.stop()

            JmsTestUtil.assertAllQueuesAreEmpty()

            // Verifiser at listener ikke mottar meldinger etter stop
            val jmsMessage = "OS;2025;22222222222"
            JmsTestUtil.sendMessage(msg = jmsMessage, queue = forSystemQueue)

            eventually(eventuallyConfiguration) {
                DbListener.dataSource.transaction { session ->
                    ForespoerselRepository.getAllForespoersel(session) shouldBe emptyList()
                }
            }
            JmsTestUtil.getMessages(forSystemQueue).size shouldBe 1
        }

        test("onOppdateringChanged() skal ikke gjøre noe når enabled er true og listener allerede kjører") {
            // Må ha withConstantNow pga. hvis denne testen kjører fra 15.12 til 31.12, så vil det bli 2 bestillinger
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                val fnr = "55555555555"
                WiremockListener.wiremockPDLStub(WiremockListener.generatePDLResponse(fnr))

                forespoerselListener.start()

                forespoerselListener.onOppdateringChanged(enabled = true)

                // Listener skal fortsatt være kjørende og mottar meldinger
                val jmsMessage = "OS;2025;$fnr"
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

        test("onOppdateringChanged() skal ikke gjøre noe når enabled er false og listener allerede stoppet") {
            forespoerselListener.stop()

            forespoerselListener.onOppdateringChanged(enabled = false)

            val jmsMessage = "OS;2025;66666666666"
            JmsTestUtil.sendMessage(msg = jmsMessage, queue = forSystemQueue)

            eventually(eventuallyConfiguration) {
                DbListener.dataSource.transaction { session ->
                    ForespoerselRepository.getAllForespoersel(session) shouldBe emptyList()
                }
            }
            JmsTestUtil.getMessages(forSystemQueue).size shouldBe 1
        }
    })
