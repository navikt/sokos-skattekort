package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import kotlinx.serialization.json.Json

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortRequest

object BestillingBatchRepository {
    fun list(tx: TransactionalSession): List<BestillingBatch> =
        tx.list(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                """.trimMargin(),
            ),
            extractor = mapToBestillingBatch,
        )

    fun insert(
        tx: TransactionalSession,
        bestillingsreferanse: String,
        request: BestillSkattekortRequest,
    ): Long =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    |INSERT INTO bestillingsbatcher (bestillingsreferanse, data_sendt) 
                    |VALUES (:bestillingsreferanse, (CAST (:dataSendt AS JSON)))
                """.trimMargin(),
                mapOf(
                    "bestillingsreferanse" to bestillingsreferanse,
                    "dataSendt" to Json.encodeToString(request),
                ),
            ),
        ) ?: error("Failed to insert bestillingsbatch")

    fun getUnprocessedBatch(tx: TransactionalSession): BestillingBatch? =
        tx.single(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                    |WHERE status <> 'FERDIG'
                    |ORDER BY oppdatert ASC
                    |LIMIT 1
                """.trimMargin(),
            ),
            extractor = mapToBestillingBatch,
        )

    fun markAs(
        tx: TransactionalSession,
        bestillingsbatchId: Long,
        status: BestillingBatchStatus,
    ) {
        tx.run(
            queryOf(
                """
                    |UPDATE bestillingsbatcher
                    |SET status = :status, oppdatert = NOW()
                    |WHERE id = :id
                """.trimMargin(),
                mapOf(
                    "id" to bestillingsbatchId,
                    "status" to status.name,
                ),
            ).asExecute,
        )
    }

    @OptIn(ExperimentalTime::class)
    private val mapToBestillingBatch: (Row) -> BestillingBatch = { row ->
        BestillingBatch(
            id = BestillingsbatchId(row.long("id")),
            status = row.string("status"),
            bestillingsreferanse = row.string("bestillingsreferanse"),
            dataSendt = row.string("data_sendt"),
            oppdatert = row.instant("oppdatert").toKotlinInstant(),
        )
    }
}
