package no.nav.sokos.skattekort.scheduler

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@OptIn(ExperimentalTime::class)
@Serializable
data class JobTaskInfo(
    val taskId: String,
    val taskName: String,
    val executionTime: @Contextual Instant,
    val isPicked: Boolean,
    val pickedBy: String?,
    val lastFailure: @Contextual Instant?,
    val lastSuccess: @Contextual Instant?,
    val ident: String?,
)
