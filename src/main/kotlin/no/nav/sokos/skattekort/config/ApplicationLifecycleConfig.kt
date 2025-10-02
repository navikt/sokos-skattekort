package no.nav.sokos.skattekort.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) { application ->
        applicationState.ready = true

        application.log.info("Server is started")
    }

    monitor.subscribe(ApplicationStopped) { application ->
        applicationState.ready = false

        application.log.info("Server is stopped")
        // Release resources and unsubscribe from events
        application.monitor.unsubscribe(ApplicationStarted) {}
        application.monitor.unsubscribe(ApplicationStopped) {}
    }
}

class ApplicationState(
    var ready: Boolean = true,
    var alive: Boolean = true,
)
