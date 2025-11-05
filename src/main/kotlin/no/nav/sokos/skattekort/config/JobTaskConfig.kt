package no.nav.sokos.skattekort.config

import java.time.Duration
import java.time.LocalDateTime

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.task.ExecutionContext
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.Schedules.cron
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging

import no.nav.sokos.skattekort.module.skattekort.BestillingService
import no.nav.sokos.skattekort.module.utsending.UtsendingService
import no.nav.sokos.skattekort.scheduler.ScheduledTaskService
import no.nav.sokos.skattekort.util.TraceUtils.withTracerId

private val logger = KotlinLogging.logger { }
private const val JOB_TASK_SEND_BESTILLING_BATCH = "sendBestilling"
private const val JOB_TASK_SEND_UTSENDING_BATCH = "sendUtsending"

object JobTaskConfig {
    fun scheduler(
        bestillingService: BestillingService,
        utsendingService: UtsendingService,
        scheduledTaskService: ScheduledTaskService,
        dataSource: HikariDataSource,
    ): Scheduler =
        Scheduler
            .create(dataSource)
            .enableImmediateExecution()
            .registerShutdownHook()
            .startTasks(
                recurringSendBestillingBatchTask(bestillingService, scheduledTaskService),
                recurringSendUtsendingTask(utsendingService, scheduledTaskService),
            ).build()

    fun recurringSendBestillingBatchTask(
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
