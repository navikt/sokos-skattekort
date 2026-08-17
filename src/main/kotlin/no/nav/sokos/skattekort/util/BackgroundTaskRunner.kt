package no.nav.sokos.skattekort.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.ApplicationState

private val logger = KotlinLogging.logger {}

class BackgroundTaskRunner(
    private val applicationState: ApplicationState,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(action: suspend CoroutineScope.() -> Unit): Job =
        scope.launch {
            try {
                action()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                logger.error(ex) { "Exception received while launching background task. Terminating application." }
                applicationState.alive = false
                applicationState.ready = false
            }
        }

    override fun close() {
        scope.cancel("Application is stopping")
    }
}
