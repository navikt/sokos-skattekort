package no.nav.sokos.skattekort.config

import java.sql.BatchUpdateException

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.path
import io.ktor.server.response.respond

import no.nav.sokos.skattekort.security.AuthenticationException
import no.nav.sokos.skattekort.security.AuthorizationException

fun StatusPagesConfig.statusPageConfig() {
    exception<Throwable> { call, cause ->
        val (responseStatus, apiError) =
            when (cause) {
                is BadRequestException -> {
                    val jsonException = cause.findCauseOfType<JsonConvertException>()
                    createApiError(HttpStatusCode.BadRequest, jsonException?.message ?: cause.message, call)
                }

                is ClientRequestException -> createApiError(cause.response.status, cause.message, call)
                is RequestValidationException -> createApiError(HttpStatusCode.BadRequest, cause.reasons.joinToString(), call)
                is IllegalArgumentException -> createApiError(HttpStatusCode.BadRequest, cause.message, call)
                is AuthenticationException -> createApiError(HttpStatusCode.Unauthorized, cause.message, call)
                is AuthorizationException -> createApiError(HttpStatusCode.Forbidden, cause.message, call)
                is BatchUpdateException -> createApiError(HttpStatusCode.InternalServerError, "En teknisk feil har oppstått. Ta kontakt med utviklerne, detaljer er logget til TEAM LOGS", call)
                else -> createApiError(HttpStatusCode.InternalServerError, cause.message ?: "En teknisk feil har oppstått. Ta kontakt med utviklerne", call)
            }
        call.respond(responseStatus, apiError)
    }
}

private fun createApiError(
    status: HttpStatusCode,
    message: String?,
    call: ApplicationCall,
): Pair<HttpStatusCode, ApiError> =
    Pair(
        status,
        ApiError(
            timestamp = Clock.System.now(),
            status = status.value,
            error = status.description,
            message = message,
            path = call.request.path(),
        ),
    )

@Serializable
data class ApiError(
    val timestamp: @Contextual Instant,
    val status: Int,
    val error: String,
    val message: String?,
    val path: String,
)

private inline fun <reified T : Throwable> Throwable.findCauseOfType(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
