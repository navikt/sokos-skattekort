package no.nav.sokos.skattekort.util

import java.util.concurrent.Executors

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.ApplicationState

private val logger = KotlinLogging.logger {}

@OptIn(DelicateCoroutinesApi::class)
fun launchBackgroundTask(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit,
): Job =
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (ex: Exception) {
            logger.error(ex) { "Exception received while launching background task. Terminating application." }
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

/*
Use Dispatchers.Unbounded to allow unlimited number of coroutines to be dispatched. Without this
only a few will be allowed simultaneously (depending on the number of available cores) which may result
in cronjobs or Kafka-consumers not starting as intended.
*/
val Dispatchers.Unbounded get() = UnboundedDispatcher.unboundedDispatcher

class UnboundedDispatcher private constructor() : CoroutineDispatcher() {
    companion object {
        val unboundedDispatcher = UnboundedDispatcher()
    }

    private val threadPool = Executors.newCachedThreadPool()
    private val dispatcher = threadPool.asCoroutineDispatcher()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        dispatcher.dispatch(context, block)
    }
}
