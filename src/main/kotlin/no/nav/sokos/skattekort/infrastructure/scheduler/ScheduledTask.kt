package no.nav.sokos.skattekort.infrastructure.scheduler

import java.time.ZonedDateTime

data class ScheduledTask(
    val taskName: String,
    val taskInstance: String,
    val executionTime: ZonedDateTime,
    val picked: Boolean,
    val pickedBy: String?,
    val lastSuccess: ZonedDateTime?,
    val lastFailure: ZonedDateTime?,
)
