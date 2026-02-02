package no.nav.sokos.skattekort.infrastructure

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge

const val METRICS_NAMESPACE = "sokos_skattekort"

object Metrics {
    val circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
    val prometheusMeterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            .also(TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry)::bindTo)

    fun counter(
        name: String,
        helpText: String,
    ): Counter =
        Counter
            .builder()
            .name("${METRICS_NAMESPACE}_$name")
            .help(helpText)
            .withoutExemplars()
            .register(prometheusMeterRegistry.prometheusRegistry)

    fun counter(
        name: String,
        helpText: String,
        labelNames: String,
    ): Counter =
        Counter
            .builder()
            .labelNames(labelNames)
            .name("${METRICS_NAMESPACE}_$name")
            .help(helpText)
            .withoutExemplars()
            .register(prometheusMeterRegistry.prometheusRegistry)

    fun gauge(
        name: String,
        helpText: String,
    ): Gauge =
        Gauge
            .builder()
            .name("${METRICS_NAMESPACE}_$name")
            .help(helpText)
            .withoutExemplars()
            .register(prometheusMeterRegistry.prometheusRegistry)

    fun gauge(
        name: String,
        helpText: String,
        labelNames: String,
    ): Gauge =
        Gauge
            .builder()
            .labelNames(labelNames)
            .name("${METRICS_NAMESPACE}_$name")
            .help(helpText)
            .withoutExemplars()
            .register(prometheusMeterRegistry.prometheusRegistry)
}
