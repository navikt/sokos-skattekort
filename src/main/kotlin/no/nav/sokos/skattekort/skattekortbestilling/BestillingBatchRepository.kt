package no.nav.sokos.skattekort.skattekortbestilling

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

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
        dataSendt: String,
    ): Long =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    |INSERT INTO bestillingsbatcher (bestillingsreferanse, data_sendt) 
                    |VALUES (:bestillingsreferanse, (CAST (:dataSendt AS JSON)))
                """.trimMargin(),
                mapOf(
                    "bestillingsreferanse" to bestillingsreferanse,
                    "dataSendt" to dataSendt,
                ),
            ),
        ) ?: error("Failed to insert bestillingsbatch")

    fun insertOppdateringsBatch(
        tx: TransactionalSession,
        bestillingsreferanse: String,
        dataSendt: String,
    ): Long =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    |INSERT INTO bestillingsbatcher (bestillingsreferanse, data_sendt, type) 
                    |VALUES (:bestillingsreferanse, (CAST (:dataSendt AS JSON)), :type)
                """.trimMargin(),
                mapOf(
                    "bestillingsreferanse" to bestillingsreferanse,
                    "dataSendt" to dataSendt,
                    "type" to BestillingsbatchType.OPPDATERING.name,
                ),
            ),
        ) ?: error("Failed to insert bestillingsbatch")

    fun getUnprocessedBestillingsbatchList(
        tx: TransactionalSession,
        type: BestillingsbatchType,
    ): List<BestillingBatch> =
        tx.list(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                    |WHERE status = 'NY' AND type = :type
                    |ORDER BY oppdatert ASC
                """.trimMargin(),
                mapOf(
                    "type" to type.name,
                ),
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

    fun updateBestillingsbatchWithMottatteData(
        tx: TransactionalSession,
        batchId: Long,
        content: String,
    ) {
        tx.run(
            queryOf(
                """
                    |UPDATE bestillingsbatcher
                    |SET data_mottatt = :content, oppdatert = NOW()
                    |WHERE id = :id
                """.trimMargin(),
                mapOf(
                    "id" to batchId,
                    "content" to content,
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
