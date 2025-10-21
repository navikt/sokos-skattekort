package no.nav.sokos.skattekort.scheduler

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import mu.KotlinLogging

import no.nav.sokos.skattekort.module.skattekort.BestillingsService

private val logger = KotlinLogging.logger { }

class BestillSkattekortScheduler(
    private val bestillingsService: BestillingsService,
    private val jobTimeout: Duration = 1.minutes,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val parser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING))
    private val isJobRunning = AtomicBoolean(false)

    fun scheduleBestillingBatch(cronExpression: String) {
        val cronDefinition = parser.parse(cronExpression)
        val executionTime = ExecutionTime.forCron(cronDefinition)
        scope.launch {
            while (isActive) {
                try {
                    val now = ZonedDateTime.now()
                    val nextExecution = executionTime.nextExecution(now).orElse(now)
                    val delayMillis = ChronoUnit.MILLIS.between(now, nextExecution)

                    logger.info {
                        val hours = delayMillis / (1000 * 60 * 60)
                        val minutes = (delayMillis / (1000 * 60)) % 60
                        val seconds = (delayMillis / 1000) % 60
                        "Next execution scheduled at: $nextExecution (in ${hours}h ${minutes}m ${seconds}s)"
                    }
                    delay(delayMillis.coerceAtLeast(0))

                    if (isJobRunning.compareAndSet(false, true)) {
                        logger.debug { "Starting batch job execution" }
                        withTimeoutOrNull(jobTimeout.absoluteValue) {
                            bestillingsService.opprettBestillingsbatch()
                        } ?: throw TimeoutException("Job execution exceeded timeout of $jobTimeout")
                    } else {
                        logger.warn { "Skipping scheduled execution because previous job is still running" }
                    }
                } catch (e: CancellationException) {
                    logger.info { "Job cancelled" }
                    break
                } catch (e: TimeoutException) {
                    logger.error(e) { "Job timed out after $jobTimeout" }
                } catch (e: Exception) {
                    logger.error(e) { "Error executing BestillingBatch job" }
                    delay(1000)
                } finally {
                    isJobRunning.set(false)
                }
            }
        }
    }

    fun stop() {
        job.cancel()
        logger.info { "BestillSkattekortScheduler stopped" }
    }
}
