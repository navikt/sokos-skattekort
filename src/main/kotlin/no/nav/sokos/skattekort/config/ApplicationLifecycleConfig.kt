package no.nav.sokos.skattekort.config

import kotlin.properties.Delegates

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.log

fun Application.applicationLifecycleConfig(applicationState: ApplicationState) {
    monitor.subscribe(ApplicationStarted) {
        applicationState.alive = true
        it.log.info("Application is started")
    }

    monitor.subscribe(ServerReady) {
        applicationState.ready = true
        it.log.info("Server is ready")
    }

    monitor.subscribe(ApplicationStopped) {
        applicationState.alive = false
        applicationState.ready = false
        it.log.info("Application is stopped")
    }
}

class ApplicationState(
    readyInit: Boolean = false,
    var alive: Boolean = false,
    var onReady: (() -> Unit)? = null,
) {
    var ready: Boolean by Delegates.observable(readyInit) { _, oldValue, newValue ->
        if (!oldValue && newValue) {
            onReady?.invoke()
        }
    }
}
