package no.nav.sokos.skattekort.infrastructure

import javax.sql.DataSource

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Tilleggsopplysning
import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Trekkode
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk
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
            val timeSisteSkattekortStored = skattekortRepository.getLatestSkattekortUpdateTime(tx)
            // Bestillinger som ikke er løst i løpet av 30 minutter
            val earliestBestillingTime = bestillingRepository.getEarliestBestillingTime(tx)
            val earliestSentBestillingTime = bestillingRepository.getEarliestSentBestillingTime(tx)
            // Utsendinger som ikke er sendt i løpet av 5 minutter
            val earliestUnsentUtsendingTime = utsendingRepository.getEarliestUnsentUtsendingTime(tx)

//            Totalt antall innhentede skattekort
            val totalSkattekortCount: Map<ResultatForSkattekort, Int> = skattekortRepository.numberOfSkattekortByResultatForSkattekortMetrics(tx)
//            Skattekort tabell på trekkodene Lønn fra Nav
//            Skattekort tabell på trekkodene pensjon fra Nav
//            Skattekort tabell på trekkodene uføretrygd fra Nav
            val numberOfTabelltrekkByTrekkode: Map<Trekkode, Int> = skattekortRepository.numberOfForskuddstrekkWithTabelltrekkByTrekkodeMetrics(tx)
//            Skattekort med tilleggsopplysning kildeskattpensjonist
//            Skattekort med tilleggsopplysning opphold på Svalbard
            val skattekortMedTilleggsopplysning: Map<Tilleggsopplysning, Int> = skattekortRepository.numberOfSkattekortByTilleggsopplysningMetrics(tx)
//            Skattekort - frikort med beløpsgrense
//            Skattekort - frikort uten beløpsgrense
            val forskuddstrekkByType: Map<Forskuddstrekk.Companion.ForskuddstrekkType, Int> = skattekortRepository.numberOfForskuddstrekkByTypeMetrics(tx)
        }
    }
}
