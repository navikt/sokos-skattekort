package no.nav.sokos.skattekort.skattekortkonvertering

import kotlinx.serialization.json.Json

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker

class SkattekortDataRepository {
    fun insert(
        tx: TransactionalSession,
        arbeidstaker: Arbeidstaker,
        bestillingsbatchId: Long,
    ) {
        tx.update(
            queryOf(
                """
                INSERT INTO skattekort_data (arbeidstaker, bestillingsbatch_id) 
                VALUES (:arbeidstaker, :bestillingsbatchId)
                """.trimIndent(),
                mapOf(
                    "arbeidstaker" to Json.encodeToString(arbeidstaker),
                    "bestillingsbatchId" to bestillingsbatchId,
                ),
            ),
        )
    }

    fun getUnprocessedSkattekortData(tx: TransactionalSession): List<Arbeidstaker> =
        tx.list(
            queryOf(
                """
                SELECT arbeidstaker FROM skattekort_data 
                WHERE skattekort_id is null
                """.trimIndent(),
            ),
            extractor = mapToArbeidstaker,
        )

    private val mapToArbeidstaker: (Row) -> Arbeidstaker = { row ->
        val arbeidstakerJson = row.string("arbeidstaker")
        Json.decodeFromString(arbeidstakerJson)
    }
}
