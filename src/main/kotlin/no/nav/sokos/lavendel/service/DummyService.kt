package no.nav.sokos.lavendel.service

import no.nav.sokos.lavendel.domain.DummyDomain
import no.nav.sokos.lavendel.metrics.Metrics

class DummyService {
    fun sayHello(): DummyDomain {
        Metrics.exampleCounter.inc()
        return DummyDomain("This is a template for Team Motta og Beregne")
    }
}
