package no.nav.sokos.skattekort.config

import java.sql.BatchUpdateException

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
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
                is BadRequestException if (cause.cause is JsonConvertException) ->
                    createApiError(HttpStatusCode.BadRequest, "Feil i format på request body. Detaljer: ${cause.message}, ${cause.cause?.message}", call)

                is RequestValidationException -> createApiError(HttpStatusCode.BadRequest, cause.reasons.joinToString(), call)
                is IllegalArgumentException -> createApiError(HttpStatusCode.BadRequest, cause.message, call)
                is UnauthorizedException -> createApiError(HttpStatusCode.Unauthorized, cause.message, call)
                is BatchUpdateException -> createApiError(HttpStatusCode.InternalServerError, "En teknisk feil har oppstått. Ta kontakt med utviklerne, detaljer er logget til secure log", call)
                else -> createApiError(HttpStatusCode.InternalServerError, cause.message ?: "En teknisk feil har oppstått. Ta kontakt med utviklerne", call)
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
