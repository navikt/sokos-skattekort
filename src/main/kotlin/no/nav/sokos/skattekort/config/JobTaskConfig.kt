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

import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.infrastructure.MetricsService
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType
import no.nav.sokos.skattekort.skattekortdata.SkattekortDataService
import no.nav.sokos.skattekort.skattekorthenting.BestillingService
import no.nav.sokos.skattekort.util.TraceUtils.withTracerId
import no.nav.sokos.skattekort.utsending.UtsendingService

private val logger = KotlinLogging.logger { }
private const val JOB_TASK_SEND_BESTILLING_BATCH = "sendBestilling"
private const val JOB_TASK_SEND_UTSENDING_BATCH = "sendUtsending"
private const val JOB_TASK_HENT_OPPDATERTE_SKATTEKORT_BATCH = "hentOppdaterteSkattekort"
private const val JOB_TASK_FETCH_METRICS = "fetchMetrics"
private const val JOB_TASK_FORESPOERSEL_INPUT = "forespoerselInput"
private const val JOB_TASK_DELETE_SKATTEKORT = "deleteSkattekort"

object JobTaskConfig {
    private var handleJobs: Boolean = true

    fun scheduler(
        bestillingService: BestillingService,
        bestillingsbatchService: BestillingsbatchService,
        utsendingService: UtsendingService,
        skattekortdataService: SkattekortDataService,
        metricsService: MetricsService,
        forespoerselService: ForespoerselService,
        skattekortService: SkattekortService,
        dataSource: DataSource,
        unleashIntegration: UnleashIntegration,
    ): Scheduler =
        Scheduler
            .create(dataSource)
            .enableImmediateExecution()
            .registerShutdownHook()
            .pollUsingLockAndFetch(0.5, 1.0)
            .startTasks(
                recurringBestillingManagementBatchTask(bestillingService, bestillingsbatchService, skattekortdataService, unleashIntegration),
                recurringSendUtsendingTask(utsendingService, unleashIntegration),
                recurringHentOppdaterteSkattekortBatchTask(bestillingService, bestillingsbatchService, unleashIntegration),
                recurringFetchMetricsTask(metricsService),
                recurringFetchForespoerselInputTask(forespoerselService, unleashIntegration),
                recurringDeleteSkattekort(skattekortService),
            ).build()

    fun recurringBestillingManagementBatchTask(
        bestillingService: BestillingService,
        bestillingsbatchService: BestillingsbatchService,
        skattekortdataService: SkattekortDataService,
        unleashIntegration: UnleashIntegration,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_SEND_BESTILLING_BATCH,
                cron(schedulerProperties.cronBestilling),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                if (handleJobs && unleashIntegration.isBestillingerEnabled()) {
                    withTracerId {
                        showLog(showLogLocalTime, instance, context)
                        val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                        bestillingService.hentBestillingsbatcher(BestillingsbatchType.BESTILLING)
                        skattekortdataService.processSkattekortData()
                        bestillingsbatchService.bestillSkattekort()
                    }
                }
            }
    }

    fun recurringSendUtsendingTask(
        utsendingService: UtsendingService,
        unleashIntegration: UnleashIntegration,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val startTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_SEND_UTSENDING_BATCH,
                cron(schedulerProperties.cronUtsending),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                if (handleJobs && unleashIntegration.isUtsendingEnabled()) {
                    withTracerId {
                        try {
                            showLog(startTime, instance, context)
                            val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                            utsendingService.handleUtsending()
                        } catch (_: Exception) {
                            // Spis exception for å ta kontroll over logging
                        }
                    }
                }
            }
    }

    fun recurringHentOppdaterteSkattekortBatchTask(
        bestillingService: BestillingService,
        bestillingsbatchService: BestillingsbatchService,
        unleashIntegration: UnleashIntegration,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_HENT_OPPDATERTE_SKATTEKORT_BATCH,
                cron(schedulerProperties.cronHentOppdaterte),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                if (handleJobs && unleashIntegration.isOppdateringEnabled()) {
                    withTracerId {
                        showLog(showLogLocalTime, instance, context)
                        val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                        bestillingService.hentBestillingsbatcher(BestillingsbatchType.OPPDATERING)
                        bestillingsbatchService.bestillOppdaterteSkattekort()
                    }
                }
            }
    }

    fun recurringFetchMetricsTask(
        metricsService: MetricsService,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_FETCH_METRICS,
                cron(schedulerProperties.cronFetchMetrics),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                if (handleJobs) {
                    withTracerId {
                        showLog(showLogLocalTime, instance, context)
                        metricsService.fetchMetrics()
                    }
                }
            }
    }

    fun recurringDeleteSkattekort(
        skattekortService: SkattekortService,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()
        return Tasks
            .recurring(
                JOB_TASK_DELETE_SKATTEKORT,
                cron(schedulerProperties.cronDeleteSkattekort),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                if (handleJobs) {
                    withTracerId {
                        showLog(showLogLocalTime, instance, context)
                        skattekortService.deleteSkattekortForYear()
                    }
                }
            }
    }

    fun recurringFetchForespoerselInputTask(
        forespoerselService: ForespoerselService,
        unleashIntegration: UnleashIntegration,
        schedulerProperties: PropertiesConfig.SchedulerProperties = PropertiesConfig.SchedulerProperties(),
    ): RecurringTask<String> {
        val showLogLocalTime = LocalDateTime.now()

        return Tasks
            .recurring(
                JOB_TASK_FORESPOERSEL_INPUT,
                cron(schedulerProperties.cronForespoerselInput),
                String::class.java,
            ).execute { instance: TaskInstance<String>, context: ExecutionContext ->
                if (handleJobs && unleashIntegration.isForespoerselInputEnabled()) {
                    withTracerId {
                        try {
                            showLog(showLogLocalTime, instance, context)
                            val ident = instance.data ?: PropertiesConfig.getApplicationProperties().naisAppName
                            forespoerselService.cronForespoerselInput()
                        } catch (_: Exception) {
                            // Spis exception for å ta kontroll over logging
                        }
                    }
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

    init {
        if (!(PropertiesConfig.isLocal() || PropertiesConfig.isTest())) {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    handleJobs = false
                },
            )
        }
    }
}
