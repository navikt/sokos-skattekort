package no.nav.sokos.skattekort.domain.skattekort

import kotlinx.serialization.json.Json

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.skatteetaten.SkatteetatenBestillSkattekortRequest

object BestillingBatchRepository {
    fun insert(
        tx: TransactionalSession,
        status: String,
        bestillingsreferanse: String,
        request: SkatteetatenBestillSkattekortRequest,
    ): Long =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    |INSERT INTO bestillingsbatcher (person_id, inntektsaar, fnr) 
                    |VALUES (:status, :bestillingsreferanse, :dataSendt)
                """.trimMargin(),
                mapOf(
                    "status" to status,
                    "bestillingsreferanse" to bestillingsreferanse,
                    "dataSendt" to Json.encodeToString(request),
                ),
            ),
        ) ?: error("Failed to insert bestillingsbatch")
}
