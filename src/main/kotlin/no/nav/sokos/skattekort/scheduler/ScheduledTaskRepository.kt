package no.nav.sokos.skattekort.scheduler

import kotlinx.datetime.toKotlinLocalDateTime

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class ScheduledTaskRepository(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
) {
    fun insert(
        ident: String,
        taskName: String,
    ) {
        dataSource.transaction { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO scheduled_tasks_history (ident, timestamp, task_name) 
                    VALUES (:ident, now(), :taskName)
                    """.trimIndent(),
                    mapOf(
                        "ident" to ident,
                        "taskName" to taskName,
                    ),
                ),
            )
        }
    }

    fun getLastScheduledTask(): Map<String, ScheduledTaskHistory> =
        using(sessionOf(dataSource)) { session ->
            session
                .list(
                    queryOf(
                        """
                        SELECT t.id, t.ident, t.task_name, t.timestamp
                        FROM scheduled_tasks_history t
                        JOIN (
                            SELECT task_name, MAX(timestamp) AS max_timestamp
                            FROM scheduled_tasks_history
                            GROUP BY task_name
                        ) latest_tasks ON t.task_name = latest_tasks.task_name AND t.timestamp = latest_tasks.max_timestamp;
                        """.trimIndent(),
                    ),
                ) { row ->
                    row.string("task_name") to
                        ScheduledTaskHistory(
                            id = row.string("id"),
                            ident = row.string("ident"),
                            timestamp = row.localDateTime("timestamp").toKotlinLocalDateTime(),
                            taskName = row.string("task_name"),
                        )
                }.toMap()
        }

    fun getAllScheduledTasks(): List<ScheduledTask> =
        using(sessionOf(dataSource)) { session ->
            session.list(
                queryOf(
                    """
                    select task_name,task_instance, execution_time, picked, picked_by, last_success, last_failure from scheduled_tasks
                    """.trimIndent(),
                ),
            ) { row ->
                ScheduledTask(
                    taskName = row.string("task_name"),
                    taskInstance = row.string("task_instance"),
                    executionTime = row.zonedDateTime("execution_time"),
                    picked = row.boolean("picked"),
                    pickedBy = row.stringOrNull("picked_by"),
                    lastSuccess = row.zonedDateTimeOrNull("last_success"),
                    lastFailure = row.zonedDateTimeOrNull("last_failure"),
                )
            }
        }
}
