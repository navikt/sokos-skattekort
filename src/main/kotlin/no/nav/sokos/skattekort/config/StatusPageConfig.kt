package no.nav.sokos.skattekort.config

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond

import no.nav.sokos.skattekort.exception.PersonNotFoundException

class UnauthorizedException(
    override val message: String,
) : RuntimeException(message)

fun StatusPagesConfig.statusPageConfig() {
    exception<Throwable> { call, cause ->
        val (responseStatus, apiError) =
            when (cause) {
                is PersonNotFoundException -> createApiError(HttpStatusCode.NotFound, cause.message, call)
                is RequestValidationException -> createApiError(HttpStatusCode.BadRequest, cause.reasons.joinToString(), call)
                is IllegalArgumentException -> createApiError(HttpStatusCode.BadRequest, cause.message, call)
                is UnauthorizedException -> createApiError(HttpStatusCode.Unauthorized, cause.message, call)
                else -> createApiError(HttpStatusCode.InternalServerError, cause.message ?: "En teknisk feil har oppstått. Ta kontakt med utviklerne", call)
            }

        call.application.log.error("Feilet håndtering av ${call.request.httpMethod} - ${call.request.path()} - Status=$responseStatus - Message=${cause.message}", cause)
        call.respond(responseStatus, apiError)
    }
}

@OptIn(ExperimentalTime::class)
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

@OptIn(ExperimentalTime::class)
@Serializable
data class ApiError(
    val timestamp: @Contextual Instant,
    val status: Int,
    val error: String,
    val message: String?,
    val path: String,
)
