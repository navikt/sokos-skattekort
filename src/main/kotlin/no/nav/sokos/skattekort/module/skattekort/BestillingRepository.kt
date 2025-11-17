package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator

object BestillingRepository {
    fun getAllBestilling(tx: TransactionalSession): List<Bestilling> =
        tx.list(
            queryOf(
                """
                SELECT * FROM bestillinger
                """.trimIndent(),
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
        bestillingsbatchId: Long,
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

    fun deleteProcessedBestillings(
        tx: TransactionalSession,
        batch: Long,
    ) {
        tx.run(
            queryOf(
                """
                DELETE FROM bestillinger
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

    fun getEarliestBestillingTime(tx: TransactionalSession): Bestilling? =
        tx.single(
            queryOf(
                """
                SELECT MIN(oppdatert) FROM bestillinger
                """.trimIndent(),
            ),
            extractor = mapToBestilling,
        )

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
