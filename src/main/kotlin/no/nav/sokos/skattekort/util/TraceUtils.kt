package no.nav.sokos.skattekort.util

import java.sql.BatchUpdateException

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants
import mu.KotlinLogging
import org.slf4j.MDC

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER

object TraceUtils {
    private val openTelemetry = GlobalOpenTelemetry.get()
    private val logger = KotlinLogging.logger {}

    fun <T> withTracerId(
        tracer: Tracer = openTelemetry.getTracer(this::class.java.canonicalName),
        spanName: String = "withTracerId",
        block: () -> T,
    ): T {
        val span = tracer.spanBuilder(spanName).startSpan()
        val context = span.spanContext

        // Make the span the current active span in the context
        return Context.current().with(span).makeCurrent().use { scope ->
            try {
                MDC.put(LoggingContextConstants.TRACE_ID, context.traceId)
                MDC.put(LoggingContextConstants.SPAN_ID, context.spanId)

                val result = block()
                span.setStatus(StatusCode.OK)
                result
            } catch (e: BatchUpdateException) {
                logger.error(marker = TEAM_LOGS_MARKER, e) { "Exception caught in traceutils: ${e.message}" }
                span.setStatus(StatusCode.ERROR, "BatchUpdateException, details in secure log")
                span.recordException(e)
                throw e
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
                span.recordException(e)
                throw e
            } finally {
                MDC.remove(LoggingContextConstants.TRACE_ID)
                MDC.remove(LoggingContextConstants.SPAN_ID)
                span.end()
            }
        }
    }
}
