package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import kotlinx.serialization.json.Json

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.skatteetaten.SkatteetatenBestillSkattekortRequest

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
        request: SkatteetatenBestillSkattekortRequest,
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
