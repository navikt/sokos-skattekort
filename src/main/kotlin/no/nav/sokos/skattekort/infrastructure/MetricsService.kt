package no.nav.sokos.skattekort.infrastructure

import javax.sql.DataSource

import io.prometheus.metrics.core.metrics.Gauge

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Trekkode
import no.nav.sokos.skattekort.infrastructure.Metrics.prometheusMeterRegistry
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class MetricsService(
    private val dataSource: DataSource,
) {
    fun fetchMetrics() {
        dataSource.transaction { tx ->
            // Varsel om vi ikke har fått skattekort på 24 timer
            SkattekortRepository.getSecondsSinceLatestSkattekortOpprettet(tx)?.let { timerSidenSisteSkattekortLMetric.set(it) }
            // Bestillinger som ikke er løst i løpet av 30 minutter
            sekunderSidenBestillingMetric.labelValues("eldste_usendt").set(BestillingRepository.getEarliestUnsentBestillingTime(tx))
            sekunderSidenBestillingMetric.labelValues("eldste_sendt").set(BestillingRepository.getEarliestSentBestillingTime(tx))

            // Utsendinger som ikke er sendt i løpet av 5 minutter
            sekunderSidenUtsendingMetric.set(UtsendingRepository.getSecondsSinceEarliestUnsentUtsending(tx))
//            Totalt antall innhentede skattekort
            val totalSkattekortCount: Map<ResultatForSkattekort, Int> = SkattekortRepository.numberOfSkattekortByResultatForSkattekortMetrics(tx)
            totalSkattekortCount.map { (resultat, count) ->
                totalSkattekortCountMetric.labelValues(resultat.value).set(count.toDouble())
            }

//            Skattekort tabell på trekkodene Lønn fra Nav
//            Skattekort tabell på trekkodene pensjon fra Nav
//            Skattekort tabell på trekkodene uføretrygd fra Nav
            val numberOfTabelltrekkByTrekkode: Map<Trekkode, Int> = SkattekortRepository.numberOfForskuddstrekkWithTabelltrekkByTrekkodeMetrics(tx)
            numberOfTabelltrekkByTrekkode.map { (trekkode, count) ->
                numberOfTabelltrekkByTrekkodeMetric.labelValues(trekkode.value).set(count.toDouble())
            }
//            Skattekort med tilleggsopplysning kildeskattpensjonist
//            Skattekort med tilleggsopplysning opphold på Svalbard
            val skattekortMedTilleggsopplysning: Map<Tilleggsopplysning, Int> = SkattekortRepository.numberOfSkattekortByTilleggsopplysningMetrics(tx)
            skattekortMedTilleggsopplysning.map { (tilleggsopplysning, count) ->
                skattekortMedTilleggsopplysningMetric.labelValues(tilleggsopplysning.value).set(count.toDouble())
            }
//            Skattekort - frikort med beløpsgrense
//            Skattekort - frikort uten beløpsgrense
            val frikortByBeloepsGrenseJaNei: Map<String, Int> = SkattekortRepository.numberOfFrikortMedUtenBeloepsgrense(tx)
            frikortByBeloepsGrenseJaNei.map { (type, count) ->
                frikortByBeloepsGrenseJaNeiMetric.labelValues(type).set(count.toDouble())
            }
        }
    }

    companion object {
        val timerSidenSisteSkattekortLMetric =
            gauge(
                name = "timer_siden_skattekort",
                helpText = "Timer siden siste skattekort ble lagret, kategorisert",
            )
        val sekunderSidenBestillingMetric =
            gauge(
                name = "oldest_bestilling_seconds",
                helpText = "Sekunder siden eldste bestilling",
                labelNames = "type",
            )
        val sekunderSidenUtsendingMetric =
            gauge(
                name = "oldest_usendt_utsending_seconds",
                helpText = "Sekunder siden eldste usendte utsending",
            )

        //        Totalt antall innhentede skattekort
        val totalSkattekortCountMetric =
            gauge(
                name = "total_skattekort_count",
                helpText = "Totalt antall innhentede skattekort",
                labelNames = "resultatForSkattekort",
            )
        val numberOfTabelltrekkByTrekkodeMetric =
            gauge(
                name = "antall_tabellkort_per_trekkode",
                helpText = "Totalt antall tabellkort per trekkode",
                labelNames = "trekkode",
            )
        val skattekortMedTilleggsopplysningMetric =
            gauge(
                name = "skattekort_med_tilleggsopplysning_count",
                helpText = "Skattekort med tilleggsopplysning",
                labelNames = "tilleggsopplysning",
            )

        val frikortByBeloepsGrenseJaNeiMetric =
            gauge(
                name = "frikort_by_beloep",
                helpText = "Frikort fordelt på om det er begrensning på beløp",
                labelNames = "beloepsgrense",
            )

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
}
