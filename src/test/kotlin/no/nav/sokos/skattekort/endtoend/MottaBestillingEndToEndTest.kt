package no.nav.sokos.skattekort.endtoend

import io.kotest.core.spec.style.FunSpec

class MottaBestillingEndToEndTest :
    FunSpec({

        // Dekningen fra denne filen er flyttet til:
        // - ForespoerselListenerTest: JMS-melding → listener → ForespoerselService → DB (MQ-listener-integrasjon)
        // - ForespoerselServiceTest: service-laget inkl. opprettelse av forespoersel, abonnement, bestilling og utsending
    })
