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

import no.nav.sokos.skattekort.module.skattekort.BestillingsService
import no.nav.sokos.skattekort.scheduler.ScheduledTaskService
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.util.TraceUtils.withTracerId

private val logger = KotlinLogging.logger { }
private const val JOB_TASK_BESTILL_SKATTEKORT = "bestill-skattekort-job-task"

object JobTaskConfig {
    fun scheduler(dataSource: HikariDataSource = DatabaseConfig.dataSource): Scheduler =
        Scheduler
            .create(dataSource)
            .enableImmediateExecution()
            .registerShutdownHook()
            .startTasks(
                recurringSomethingsomething(),
            ).build()

    fun recurringSomethingsomething(
        bestillingsService: BestillingsService =
            BestillingsService(
                dataSource = DatabaseConfig.dataSource,
                skatteetatenClient = SkatteetatenClient(MaskinportenTokenClient(httpClient), httpClient),
            ),
        scheduledTaskService: ScheduledTaskService = ScheduledTaskService(),
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_BESTILL_SKATTEKORT,
                cron(schedulerProperties.cronExpression),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                withTracerId {
                    showLog(showLogLocalTime, instance, context)
                    val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                    scheduledTaskService.insertScheduledTaskHistory(ident, JOB_TASK_BESTILL_SKATTEKORT)
                    bestillingsService.opprettBestillingsbatch()
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
