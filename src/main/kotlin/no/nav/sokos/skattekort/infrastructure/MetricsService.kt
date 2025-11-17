package no.nav.sokos.skattekort.infrastructure

import java.time.Duration
import java.time.LocalDateTime
import javax.sql.DataSource

import io.prometheus.metrics.core.metrics.Gauge

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Tilleggsopplysning
import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Trekkode
import no.nav.sokos.skattekort.infrastructure.Metrics.prometheusMeterRegistry
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class MetricsService(
    private val dataSource: DataSource,
    private val skattekortRepository: SkattekortRepository,
    private val bestillingRepository: BestillingRepository,
    private val utsendingRepository: UtsendingRepository,
) {
    fun fetchMetrics() {
        dataSource.transaction { tx ->
            // Varsel om vi ikke har fått skattekort på 24 timer
            val naa = LocalDateTime.now()
            val timeSisteSkattekortStored: LocalDateTime = skattekortRepository.getLatestSkattekortUpdateTime(tx) ?: naa
            timerSidenSisteSkattekortLMetric.set(Duration.between(timeSisteSkattekortStored, naa).toMinutes().div(60.0))
            // Bestillinger som ikke er løst i løpet av 30 minutter
            val earliestBestillingTime: LocalDateTime = bestillingRepository.getEarliestBestillingTime(tx) ?: naa
            minutterSidenBestillingMetric.labelValues("eldste_lagret").set(Duration.between(earliestBestillingTime, naa).toSeconds().div(60.0))
            val earliestSentBestillingTime = bestillingRepository.getEarliestSentBestillingTime(tx) ?: naa
            minutterSidenBestillingMetric.labelValues("eldste_sendt").set(Duration.between(earliestSentBestillingTime, naa).toSeconds().div(60.0))
            // Utsendinger som ikke er sendt i løpet av 5 minutter
            val earliestUnsentUtsendingTime = utsendingRepository.getEarliestUnsentUtsendingTime(tx) ?: naa
            minutterSidenUtsendingMetric.set(Duration.between(earliestUnsentUtsendingTime, naa).toSeconds().div(60.0))
//            Totalt antall innhentede skattekort
            val totalSkattekortCount: Map<ResultatForSkattekort, Int> = skattekortRepository.numberOfSkattekortByResultatForSkattekortMetrics(tx)
            totalSkattekortCount.map { (resultat, count) ->
                totalSkattekortCountMetric.labelValues(resultat.value).set(count.toDouble())
            }

//            Skattekort tabell på trekkodene Lønn fra Nav
//            Skattekort tabell på trekkodene pensjon fra Nav
//            Skattekort tabell på trekkodene uføretrygd fra Nav
            val numberOfTabelltrekkByTrekkode: Map<Trekkode, Int> = skattekortRepository.numberOfForskuddstrekkWithTabelltrekkByTrekkodeMetrics(tx)
            numberOfTabelltrekkByTrekkode.map { (trekkode, count) ->
                numberOfTabelltrekkByTrekkodeMetric.labelValues(trekkode.value).set(count.toDouble())
            }
//            Skattekort med tilleggsopplysning kildeskattpensjonist
//            Skattekort med tilleggsopplysning opphold på Svalbard
            val skattekortMedTilleggsopplysning: Map<Tilleggsopplysning, Int> = skattekortRepository.numberOfSkattekortByTilleggsopplysningMetrics(tx)
            skattekortMedTilleggsopplysning.map { (tilleggsopplysning, count) ->
                skattekortMedTilleggsopplysningMetric.labelValues(tilleggsopplysning.value).set(count.toDouble())
            }
//            Skattekort - frikort med beløpsgrense
//            Skattekort - frikort uten beløpsgrense
            val frikortByBeloepsGrenseJaNei: Map<String, Int> = skattekortRepository.numberOfFrikortMedUtenBeloepsgrense(tx)
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
        val minutterSidenBestillingMetric =
            gauge(
                name = "oldest_bestilling_minutes",
                helpText = "Minutter siden eldste bestilling",
            )
        val minutterSidenUtsendingMetric =
            gauge(
                name = "oldest_usendt_utsending_minutes",
                helpText = "Minutter siden eldste usendte utsending",
            )

        //        Totalt antall innhentede skattekort
        val totalSkattekortCountMetric =
            gauge(
                name = "total_skattekort_count",
                helpText = "Totalt antall innhentede skattekort",
            )
        val numberOfTabelltrekkByTrekkodeMetric =
            gauge(
                name = "antall_tabellkort_per_trekkode",
                helpText = "Totalt antall tabellkort per trekkode",
            )
        val skattekortMedTilleggsopplysningMetric =
            gauge(
                name = "skattekort_med_tilleggsopplysning_count",
                helpText = "Skattekort med tilleggsopplysning",
            )

        val frikortByBeloepsGrenseJaNeiMetric =
            gauge(
                name = "frikort_by_beloep",
                helpText = "Frikort fordelt på om det er begrensning på beløp",
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
    }
}
