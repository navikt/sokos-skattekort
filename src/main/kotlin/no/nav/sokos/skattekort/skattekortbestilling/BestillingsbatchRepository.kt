package no.nav.sokos.skattekort.skattekortbestilling

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

object BestillingsbatchRepository {
    fun insert(
        tx: TransactionalSession,
        bestillingsreferanse: String,
        dataSendt: String,
        type: BestillingsbatchType,
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
                    "type" to type.name,
                ),
            ),
        ) ?: error("Failed to insert bestillingsbatch")

    fun getAllUnprocessedBestillingsbatch(
        tx: TransactionalSession,
        type: BestillingsbatchType,
    ): List<Bestillingsbatch> =
        tx.list(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                    |WHERE status IN ('${BestillingsbatchStatus.NY}', '${BestillingsbatchStatus.RETRY}') AND type = :type
                    |ORDER BY oppdatert ASC
                """.trimMargin(),
                mapOf(
                    "type" to type.name,
                ),
            ),
            extractor = mapToBestillingsbatch,
        )

    fun findById(
        tx: TransactionalSession,
        bestillingsbatchId: Long,
    ): Bestillingsbatch? =
        tx.single(
            queryOf(
                """
                    |SELECT * 
                    |FROM bestillingsbatcher
                    |WHERE id = :id
                """.trimMargin(),
                mapOf("id" to bestillingsbatchId),
            ),
            extractor = mapToBestillingsbatch,
        )

    fun markAs(
        tx: TransactionalSession,
        bestillingsbatchId: Long,
        status: BestillingsbatchStatus,
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

    fun updateBestillingsbatchWithMottatteData(
        tx: TransactionalSession,
        batchId: Long,
        dataMottatt: String,
    ) {
        tx.run(
            queryOf(
                """
                    |UPDATE bestillingsbatcher
                    |SET data_mottatt = :dataMottatt, oppdatert = NOW()
                    |WHERE id = :id
                """.trimMargin(),
                mapOf(
                    "id" to batchId,
                    "dataMottatt" to dataMottatt,
                ),
            ).asExecute,
        )
    }

    fun getLastBestilingsbatch(tx: TransactionalSession): Bestillingsbatch? =
        tx.single(
            queryOf(
                """
                SELECT * FROM bestillingsbatcher WHERE status IN ('${BestillingsbatchStatus.NY}', '${BestillingsbatchStatus.RETRY}') ORDER BY opprettet DESC LIMIT 1
                """.trimIndent(),
            ),
            extractor = mapToBestillingsbatch,
        )

    @OptIn(ExperimentalTime::class)
    val mapToBestillingsbatch: (Row) -> Bestillingsbatch = { row ->
        Bestillingsbatch(
            id = BestillingsbatchId(row.long("id")),
            status = BestillingsbatchStatus.valueOf(row.string("status")),
            type = BestillingsbatchType.valueOf(row.string("type")),
            bestillingsreferanse = row.string("bestillingsreferanse"),
            dataSendt = row.string("data_sendt"),
            oppdatert = row.instant("oppdatert").toKotlinInstant(),
            opprettet = row.instant("opprettet").toKotlinInstant(),
        )
    }
}
