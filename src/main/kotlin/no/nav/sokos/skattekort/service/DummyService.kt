package no.nav.sokos.skattekort.service

import no.nav.sokos.skattekort.domain.DummyDomain
import no.nav.sokos.skattekort.metrics.Metrics

class DummyService {
    fun sayHello(): DummyDomain {
        Metrics.exampleCounter.inc()
        return DummyDomain("This is a template for Team Motta og Beregne")
    }
}
