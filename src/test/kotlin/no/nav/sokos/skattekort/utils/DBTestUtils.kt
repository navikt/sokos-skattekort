package no.nav.sokos.skattekort.utils

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchRepository.mapToBestillingsbatch
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingRepository.mapToBestilling

object DBTestUtils {
    fun getAllBestillingsbatch(tx: TransactionalSession): List<Bestillingsbatch> =
        tx.list(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                """.trimMargin(),
            ),
            extractor = mapToBestillingsbatch,
        )

    fun getAllBestilling(tx: TransactionalSession): List<Bestilling> =
        tx.list(
            queryOf(
                """
                SELECT * FROM bestillinger
                """.trimIndent(),
            ),
            extractor = mapToBestilling,
        )
}
