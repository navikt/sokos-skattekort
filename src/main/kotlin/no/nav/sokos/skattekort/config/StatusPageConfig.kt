package no.nav.sokos.skattekort.config

import java.sql.BatchUpdateException

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import mu.KotlinLogging

class UnauthorizedException(
    override val message: String,
) : RuntimeException(message)

private val logger = KotlinLogging.logger { }

fun StatusPagesConfig.statusPageConfig() {
    exception<Throwable> { call, cause ->
        val (responseStatus, apiError) =
            when (cause) {
                is RequestValidationException -> {
                    logger.error("Feilet håndtering av ${call.request.httpMethod} - ${call.request.path()} - Status=${HttpStatusCode.BadRequest} - Message=${cause.message}", cause)
                    createApiError(HttpStatusCode.BadRequest, cause.reasons.joinToString(), call)
                }

                is IllegalArgumentException -> {
                    logger.error("Feilet håndtering av ${call.request.httpMethod} - ${call.request.path()} - Status=${HttpStatusCode.BadRequest} - Message=${cause.message}", cause)
                    createApiError(HttpStatusCode.BadRequest, cause.message, call)
                }

                is UnauthorizedException -> {
                    logger.error("Feilet håndtering av ${call.request.httpMethod} - ${call.request.path()} - Status=${HttpStatusCode.Unauthorized} - Message=${cause.message}", cause)
                    createApiError(HttpStatusCode.Unauthorized, cause.message, call)
                }

                is BatchUpdateException -> {
                    logger.error(marker = TEAM_LOGS_MARKER, cause) { "BatchUpdateException fanget, message er ${cause.message}" }
                    createApiError(HttpStatusCode.InternalServerError, "En teknisk feil har oppstått. Ta kontakt med utviklerne, detaljer er logget til secure log", call)
                }

                else -> {
                    logger.error("Feilet håndtering av ${call.request.httpMethod} - ${call.request.path()} - Status=${HttpStatusCode.InternalServerError} - Message=${cause.message}", cause)
                    createApiError(HttpStatusCode.InternalServerError, cause.message ?: "En teknisk feil har oppstått. Ta kontakt med utviklerne", call)
                }
            }

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
