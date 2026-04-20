package no.nav.sokos.skattekort.config

import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.Json

import com.auth0.jwt.JWT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import mu.KotlinLogging
import org.slf4j.MarkerFactory
import org.slf4j.event.Level

import no.nav.sokos.skattekort.api.model.requestValidationOpprettSkattekortRequest
import no.nav.sokos.skattekort.api.model.requestValidationSkattekortConfig
import no.nav.sokos.skattekort.api.model.requestValidationSkattekortRequest
import no.nav.sokos.skattekort.infrastructure.Metrics
import no.nav.sokos.skattekort.security.TokenUtils

val RECENT_BATCH_GRACE_PERIOD = 1.hours
val TEAM_LOGS_MARKER = MarkerFactory.getMarker("TEAM_LOGS")
private const val X_KALLENDE_SYSTEM = "x-kallende-system"

private val logger = KotlinLogging.logger {}

val jsonConfig =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

fun Application.commonConfig() {
    install(CallLogging) {
        logger = no.nav.sokos.skattekort.config.logger
        level = Level.INFO
        mdc(X_KALLENDE_SYSTEM) { it.extractCallingSystemFromJwtToken() }
        filter { call -> call.request.path().startsWith("/api") }
        disableDefaultColors()
    }
    install(StatusPages) {
        statusPageConfig()
    }
    install(ContentNegotiation) {
        json(
            jsonConfig,
        )
    }
    install(RequestValidation) {
        requestValidationSkattekortConfig()
        requestValidationSkattekortRequest()
        requestValidationOpprettSkattekortRequest()
    }
    install(MicrometerMetrics) {
        registry = Metrics.prometheusMeterRegistry
        meterBinders =
            listOf(
                UptimeMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ProcessorMetrics(),
            )
    }
}

fun Routing.internalNaisRoutes(
    applicationState: ApplicationState,
    readynessCheck: () -> Boolean = { applicationState.ready },
    alivenessCheck: () -> Boolean = { applicationState.alive },
) {
    route("internal") {
        get("isAlive") {
            when (alivenessCheck()) {
                true -> call.respondText { "I'm alive :)" }
                else ->
                    call.respondText(
                        text = "I'm dead x_x",
                        status = HttpStatusCode.InternalServerError,
                    )
            }
        }
        get("isReady") {
            when (readynessCheck()) {
                true -> call.respondText { "I'm ready! :)" }
                else ->
                    call.respondText(
                        text = "Wait! I'm not ready yet! :O",
                        status = HttpStatusCode.InternalServerError,
                    )
            }
        }
        get("metrics") {
            call.respondText(Metrics.prometheusMeterRegistry.scrape())
        }
    }
}

/**
 * Extract calling system name from JWT token for MDC logging.
 * NOTE: This runs BEFORE authentication, so we must manually decode the token.
 * For endpoint-level usage AFTER authentication, use AuthorizationGuard.getCallingSystem() instead.
 *
 * Uses azp_name (authorized party name) or client_id as fallback.
 * Strips namespace/cluster prefix (e.g., "cluster:namespace:app" -> "app").
 */
private fun ApplicationCall.extractCallingSystemFromJwtToken(): String {
    val token = request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")
    val azpNameOrClientId =
        token?.let { tokenString ->
            runCatching {
                JWT.decode(tokenString)
            }.onFailure { error ->
                logger.warn("Failed to decode token: ", error)
            }.getOrNull()
                ?.let { decodedJWT ->
                    decodedJWT.claims["azp_name"]?.asString() ?: decodedJWT.claims["client_id"]?.asString()
                }
        }
    return TokenUtils.extractApplicationName(azpNameOrClientId)
}
