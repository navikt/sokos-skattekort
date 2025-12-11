package no.nav.sokos.skattekort.config

import java.time.Duration
import java.time.LocalDateTime
import javax.sql.DataSource

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task.ExecutionContext
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.Schedules.cron
import mu.KotlinLogging

import no.nav.sokos.skattekort.infrastructure.MetricsService
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.skattekort.BestillingService
import no.nav.sokos.skattekort.module.utsending.UtsendingService
import no.nav.sokos.skattekort.scheduler.ScheduledTaskService
import no.nav.sokos.skattekort.util.TraceUtils.withTracerId

private val logger = KotlinLogging.logger { }
private const val JOB_TASK_SEND_BESTILLING_BATCH = "sendBestilling"
private const val JOB_TASK_SEND_UTSENDING_BATCH = "sendUtsending"
private const val JOB_TASK_HENT_OPPDATERTE_SKATTEKORT_BATCH = "hentOppdaterteSkattekort"
private const val JOB_TASK_FETCH_METRICS = "fetchMetrics"
private const val JOB_TASK_FORESPOERSEL_INPUT = "forespoerselInput"

object JobTaskConfig {
    fun scheduler(
        bestillingService: BestillingService,
        utsendingService: UtsendingService,
        scheduledTaskService: ScheduledTaskService,
        metricsService: MetricsService,
        forespoerselService: ForespoerselService,
        dataSource: DataSource,
    ): Scheduler =
        Scheduler
            .create(dataSource)
            .enableImmediateExecution()
            .registerShutdownHook()
            .pollUsingLockAndFetch(0.5, 1.0)
            .startTasks(
                recurringBestillingManagementBatchTask(bestillingService, scheduledTaskService),
                recurringSendUtsendingTask(utsendingService, scheduledTaskService),
                recurringHentOppdaterteSkattekortBatchTask(bestillingService, scheduledTaskService),
                recurringFetchMetricsTask(metricsService, scheduledTaskService),
                recurringFetchForespoerselInputTask(forespoerselService, scheduledTaskService),
            ).build()

    fun recurringBestillingManagementBatchTask(
        bestillingService: BestillingService,
        scheduledTaskService: ScheduledTaskService,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_SEND_BESTILLING_BATCH,
                cron(schedulerProperties.cronBestilling),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                withTracerId {
                    showLog(showLogLocalTime, instance, context)
                    val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                    scheduledTaskService.insertScheduledTaskHistory(ident, JOB_TASK_SEND_BESTILLING_BATCH)
                    bestillingService.hentSkattekort()
                    bestillingService.opprettBestillingsbatch()
                }
            }
    }

    fun recurringSendUtsendingTask(
        utsendingService: UtsendingService,
        scheduledTaskService: ScheduledTaskService,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val startTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_SEND_UTSENDING_BATCH,
                cron(schedulerProperties.cronUtsending),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                withTracerId {
                    showLog(startTime, instance, context)
                    val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                    scheduledTaskService.insertScheduledTaskHistory(ident, JOB_TASK_SEND_UTSENDING_BATCH)
                    utsendingService.handleUtsending()
                }
            }
    }

    fun recurringHentOppdaterteSkattekortBatchTask(
        bestillingService: BestillingService,
        scheduledTaskService: ScheduledTaskService,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_HENT_OPPDATERTE_SKATTEKORT_BATCH,
                cron(schedulerProperties.cronHentOppdaterte),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                withTracerId {
                    showLog(showLogLocalTime, instance, context)
                    val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                    scheduledTaskService.insertScheduledTaskHistory(ident, JOB_TASK_HENT_OPPDATERTE_SKATTEKORT_BATCH)
                    bestillingService.hentOppdaterteSkattekort()
                }
            }
    }

    fun recurringFetchMetricsTask(
        metricsService: MetricsService,
        scheduledTaskService: ScheduledTaskService,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_FETCH_METRICS,
                cron(schedulerProperties.cronFetchMetrics),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                withTracerId {
                    showLog(showLogLocalTime, instance, context)
                    val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                    scheduledTaskService.insertScheduledTaskHistory(ident, JOB_TASK_FETCH_METRICS)
                    metricsService.fetchMetrics()
                }
            }
    }

    fun recurringFetchForespoerselInputTask(
        forespoerselService: ForespoerselService,
        scheduledTaskService: ScheduledTaskService,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()

        return Tasks
            .recurring(
                JOB_TASK_FORESPOERSEL_INPUT,
                cron(schedulerProperties.cronForespoerselInput),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                withTracerId {
                    showLog(showLogLocalTime, instance, context)
                    val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                    scheduledTaskService.insertScheduledTaskHistory(ident, JOB_TASK_FORESPOERSEL_INPUT)
                    forespoerselService.cronForespoerselInput()
                }
            }
    }

    private fun <T> showLog(
        localtime: LocalDateTime,
        instance: TaskInstance<T>,
        context: ExecutionContext,
    ): LocalDateTime {
        if (localtime.plusMinutes(Duration.ofMinutes(5).toMinutes()).isBefore(LocalDateTime.now())) {
            logger.info { "Kjør skedulering med instans: $instance, jobbnavn: $context" }
            return LocalDateTime.now()
        }
        return localtime
    }
}
