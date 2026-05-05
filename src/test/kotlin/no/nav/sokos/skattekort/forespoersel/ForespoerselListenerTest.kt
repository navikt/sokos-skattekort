package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDateTime

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.activemq.artemis.jms.client.ActiveMQQueue

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.MockHttpClient
import no.nav.sokos.skattekort.utils.MockResponse
import no.nav.sokos.skattekort.utils.TestUtils.eventuallyConfiguration
import no.nav.sokos.skattekort.utils.azuredTokenClient
import no.nav.sokos.skattekort.utils.generateHentIdenterBolk

class ForespoerselListenerTest :
    FunSpec({
        extensions(listOf(MQListener, DbListener))

        val forSystemQueue = ActiveMQQueue("FOR_SYSTEM")
        val forSystemBOQQueue = ActiveMQQueue("FOR_SYSTEM_BOQ")

        fun createForespoerselListener(vararg responses: MockResponse): ForespoerselListener {
            val engine = MockHttpClient.getEngine(*responses)
            val client = MockHttpClient.getClient(engine)
            val pdlClientService = PdlClientService(httpClient = client, pdlUrl = "http://localhost", azuredTokenClient = azuredTokenClient)
            return ForespoerselListener(
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

        test("start() skal opprette JMS context og consumer") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                val fnr = "11111111111"
                val forespoerselListener = createForespoerselListener(MockResponse("/graphql", generateHentIdenterBolk(fnr)))

                forespoerselListener.onOppdateringChanged(true)

                try {
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
                } finally {
                    forespoerselListener.onOppdateringChanged(false)
                }
            }
        }

        test("stop() skal lukke JMS consumer og context") {
            val forespoerselListener = createForespoerselListener()

            forespoerselListener.onOppdateringChanged(true)
            forespoerselListener.onOppdateringChanged(false)

            JmsTestUtil.assertAllQueuesAreEmpty()

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
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                val fnr = "55555555555"
                val forespoerselListener = createForespoerselListener(MockResponse("/graphql", generateHentIdenterBolk(fnr)))

                forespoerselListener.onOppdateringChanged(enabled = true)

                try {
                    forespoerselListener.onOppdateringChanged(enabled = true)

                    val jmsMessage = "OS;2025;$fnr"
                    JmsTestUtil.sendMessage(msg = jmsMessage, queue = forSystemQueue)

                    eventually(eventuallyConfiguration) {
                        DbListener.dataSource.transaction { session ->
                            ForespoerselRepository.getAllForespoersel(session).any {
                                it.dataMottatt == jmsMessage
                            } shouldBe true
                        }
                    }
                } finally {
                    forespoerselListener.onOppdateringChanged(false)
                }
            }
        }

        test("onOppdateringChanged() skal ikke gjøre noe når enabled er false og listener allerede stoppet") {
            val forespoerselListener = createForespoerselListener()

            forespoerselListener.onOppdateringChanged(enabled = false)

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
