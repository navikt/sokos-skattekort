package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator

object BestillingRepository {
    fun getBestillingsKandidaterForBatch(tx: TransactionalSession): List<Bestilling> =
        tx.list(
            queryOf(
                """
                SELECT * FROM bestillinger
                """.trimIndent(),
            ),
            extractor = mapToBestilling,
        )

    fun getBestillingsKandidaterForBatch(
        tx: TransactionalSession,
        maxYear: Int,
    ): List<Bestilling> =
        tx.list(
            queryOf(
                """
                SELECT b.* FROM bestillinger b
                WHERE b.inntektsaar <= :maxYear
                AND b.inntektsaar = (SELECT MIN(b2.inntektsaar) FROM bestillinger b2 WHERE b2.bestillingsbatch_id IS NULL)
                AND b.bestillingsbatch_id IS NULL
                LIMIT 1000
                """.trimIndent(),
                mapOf("maxYear" to maxYear),
            ),
            extractor = mapToBestilling,
        )

    fun insert(
        tx: TransactionalSession,
        bestilling: Bestilling,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    |INSERT INTO bestillinger (person_id, inntektsaar, fnr) 
                    |VALUES (:personId, :inntektsaar, :fnr)
                """.trimMargin(),
                mapOf(
                    "personId" to bestilling.personId.value,
                    "inntektsaar" to bestilling.inntektsaar,
                    "fnr" to bestilling.fnr.value,
                ),
            ),
        )

    fun updateBestillingsWithBatchId(
        tx: TransactionalSession,
        bestillingsIds: List<Long>,
        bestillingsbatchId: Long?,
    ) {
        if (bestillingsIds.isEmpty()) return
        tx.batchPreparedNamedStatement(
            """
            UPDATE bestillinger
            SET bestillingsbatch_id = :bestillingsbatchId
            WHERE id = :id
            """.trimIndent(),
            bestillingsIds.map { mapOf("id" to it, "bestillingsbatchId" to bestillingsbatchId) },
        )
    }

    fun deleteProcessedBestilling(
        tx: TransactionalSession,
        batch: Long,
        fnr: String,
    ) {
        tx.run(
            queryOf(
                """
                DELETE FROM bestillinger
                WHERE bestillingsbatch_id = :bestillingsbatchId
                AND fnr = :fnr
                """.trimIndent(),
                mapOf(
                    "bestillingsbatchId" to batch,
                    "fnr" to fnr,
                ),
            ).asUpdate,
        )
    }

    fun retryUnprocessedBestillings(
        tx: TransactionalSession,
        batch: Long,
    ) {
        tx.run(
            queryOf(
                """
                UPDATE bestillinger SET bestillingsbatch_id = null 
                WHERE bestillingsbatch_id = :bestillingsbatchId
                """.trimIndent(),
                mapOf("bestillingsbatchId" to batch),
            ).asUpdate,
        )
    }

    fun findByPersonIdAndInntektsaar(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
    ): Bestilling? =
        tx.single(
            queryOf(
                """
                SELECT * FROM bestillinger
                WHERE person_id = :personId AND inntektsaar = :inntektsaar
                """.trimIndent(),
                mapOf(
                    "personId" to personId.value,
                    "inntektsaar" to inntektsaar,
                ),
            ),
            extractor = mapToBestilling,
        )

    fun getAllBestillingsInBatch(
        tx: TransactionalSession,
        batchId: Long,
    ) = tx.list(
        queryOf(
            """
            SELECT * FROM bestillinger
            WHERE bestillingsbatch_id = :bestillingsbatchId
            """.trimIndent(),
            mapOf(
                "bestillingsbatchId" to batchId,
            ),
        ),
        extractor = mapToBestilling,
    )

    fun getEarliestUnsentBestillingTime(tx: TransactionalSession): Double =
        tx.single(
            queryOf(
                """
                SELECT EXTRACT(EPOCH FROM NOW() - COALESCE(MIN(oppdatert), NOW())) as earliest_oppdatert FROM bestillinger
                WHERE bestillingsbatch_id IS NULL
                """.trimIndent(),
            ),
            extractor = { row -> row.double("earliest_oppdatert") },
        ) ?: error("Should always return something")

    fun getEarliestSentBestillingTime(tx: TransactionalSession): Double =
        tx.single(
            queryOf(
                """
                SELECT EXTRACT(EPOCH FROM NOW() - COALESCE(MIN(oppdatert), NOW())) as earliest_oppdatert FROM bestillinger
                WHERE bestillingsbatch_id IS NOT NULL
                """.trimIndent(),
            ),
            extractor = { row -> row.double("earliest_oppdatert") },
        ) ?: error("Should always return something")

    @OptIn(ExperimentalTime::class)
    private val mapToBestilling: (Row) -> Bestilling = { row ->
        Bestilling(
            id = BestillingId(row.long("id")),
            personId = PersonId(row.long("person_id")),
            fnr = Personidentifikator(row.string("fnr")),
            inntektsaar = row.int("inntektsaar"),
            bestillingsbatchId = row.longOrNull("bestillingsbatch_id")?.let { BestillingsbatchId(it) },
            oppdatert = row.instant("oppdatert").toKotlinInstant(),
        )
    }
}
