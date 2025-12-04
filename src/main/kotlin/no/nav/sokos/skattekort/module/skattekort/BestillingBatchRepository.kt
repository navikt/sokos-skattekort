package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import kotlinx.serialization.json.Json

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortRequest

private const val OPPDATERING = "OPPDATERING"

private const val BESTILLING = "BESTILLING"

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

    fun insertBestillingsBatch(
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

    fun insertOppdateringsBatch(
        tx: TransactionalSession,
        bestillingsreferanse: String,
        request: BestillSkattekortRequest,
    ): Long =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    |INSERT INTO bestillingsbatcher (bestillingsreferanse, data_sendt, type) 
                    |VALUES (:bestillingsreferanse, (CAST (:dataSendt AS JSON)), :type)
                """.trimMargin(),
                mapOf(
                    "bestillingsreferanse" to bestillingsreferanse,
                    "dataSendt" to Json.encodeToString(request),
                    "type" to OPPDATERING,
                ),
            ),
        ) ?: error("Failed to insert bestillingsbatch")

    fun getUnprocessedBestillingsBatch(tx: TransactionalSession): BestillingBatch? =
        tx.single(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                    |WHERE status = 'NY' AND type = '$BESTILLING'
                    |ORDER BY oppdatert ASC
                    |LIMIT 1
                """.trimMargin(),
            ),
            extractor = mapToBestillingBatch,
        )

    fun getUnprocessedOppdateringsBatch(tx: TransactionalSession): BestillingBatch? =
        tx.single(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                    |WHERE status = 'NY' AND type = '$OPPDATERING'
                    |ORDER BY oppdatert ASC
                    |LIMIT 1
                """.trimMargin(),
            ),
            extractor = mapToBestillingBatch,
        )

    fun findById(
        tx: TransactionalSession,
        bestillingsbatchId: Long,
    ): BestillingBatch? =
        tx.single(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                    |WHERE id = :id
                """.trimMargin(),
                mapOf("id" to bestillingsbatchId),
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
                    "status" to status.value,
                ),
            ).asExecute,
        )
    }

    @OptIn(ExperimentalTime::class)
    private val mapToBestillingBatch: (Row) -> BestillingBatch = { row ->
        BestillingBatch(
            id = BestillingsbatchId(row.long("id")),
            status = row.string("status"),
            type = row.string("type"),
            bestillingsreferanse = row.string("bestillingsreferanse"),
            dataSendt = row.string("data_sendt"),
            oppdatert = row.instant("oppdatert").toKotlinInstant(),
            opprettet = row.instant("opprettet").toKotlinInstant(),
        )
    }
}
