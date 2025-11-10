package no.nav.sokos.skattekort.scheduler

import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

import no.nav.sokos.skattekort.util.SQLUtils.transaction

@OptIn(ExperimentalTime::class)
class ScheduledTaskService(
    private val dataSource: DataSource,
) {
    fun getScheduledTaskInformation(): List<JobTaskInfo> =
        dataSource.transaction { tx ->
            val scheduledTaskMap = ScheduledTaskRepository.getLastScheduledTask(tx)
            ScheduledTaskRepository.getAllScheduledTasks(tx).map {
                JobTaskInfo(
                    it.taskInstance,
                    it.taskName,
                    Instant.fromEpochMilliseconds(it.executionTime.toInstant().toEpochMilli()),
                    it.picked,
                    it.pickedBy,
                    it.lastFailure?.let { failure -> Instant.fromEpochMilliseconds(failure.toInstant().toEpochMilli()) },
                    it.lastSuccess?.let { lastSuccess -> Instant.fromEpochMilliseconds(lastSuccess.toInstant().toEpochMilli()) },
                    scheduledTaskMap[it.taskName]?.ident,
                )
            }
        }

    fun insertScheduledTaskHistory(
        ident: String,
        taskName: String,
    ) {
        dataSource.transaction { tx ->
            ScheduledTaskRepository.insert(ident, taskName, tx)
        }
    }
}
