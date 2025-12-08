package no.nav.sokos.skattekort.infrastructure

import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge

const val METRICS_NAMESPACE = "sokos_skattekort"

object Metrics {
    val prometheusMeterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
            config().meterFilter(MeterFilter.ignoreTags("unwanted_tag"))
        }

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
