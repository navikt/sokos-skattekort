package no.nav.sokos.skattekort.skattekorthenting

import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchId

object BestillingRepository {
    fun getAllBestilling(
        tx: TransactionalSession,
        maxYear: Int,
    ): List<Bestilling> {
        // language=SQL
        val sql =
            """
            SELECT b.* FROM bestillinger b
            WHERE b.inntektsaar <= :maxYear
            AND b.inntektsaar = (SELECT MIN(b2.inntektsaar) FROM bestillinger b2 WHERE b2.bestillingsbatch_id IS NULL)
            AND b.bestillingsbatch_id IS NULL
            LIMIT 1000
            """.trimIndent()
        return tx.list(
            queryOf(
                sql,
                mapOf("maxYear" to maxYear),
            ),
            extractor = mapToBestilling,
        )
    }

    fun getAllBestillingsForAdmin(tx: TransactionalSession): List<Bestilling> {
        // language=SQL
        val sql =
            """
            SELECT b.* FROM bestillinger b
            """.trimIndent()
        return tx.list(
            queryOf(sql),
            extractor = mapToBestilling,
        )
    }

    fun insertBatch(
        tx: TransactionalSession,
        bestillingList: List<Bestilling>,
    ) = run {
        // language=SQL
        val sql =
            """
            INSERT INTO bestillinger (person_id, inntektsaar, fnr)
            VALUES (:personId, :inntektsaar, :fnr)
            ON CONFLICT (person_id, fnr, inntektsaar) DO NOTHING
            """.trimIndent()
        tx.batchPreparedNamedStatement(
            sql,
            bestillingList.map { bestilling ->
                mapOf(
                    "personId" to bestilling.personId.value,
                    "inntektsaar" to bestilling.inntektsaar,
                    "fnr" to bestilling.fnr.value,
                )
            },
        )
    }

    fun updateBestillingsWithBatchId(
        tx: TransactionalSession,
        bestillingsIds: List<Long>,
        bestillingsbatchId: Long?,
    ) {
        if (bestillingsIds.isEmpty()) return
        // language=SQL
        val sql =
            """
            UPDATE bestillinger
            SET bestillingsbatch_id = :bestillingsbatchId
            WHERE id = :id
            """.trimIndent()
        tx.batchPreparedNamedStatement(
            sql,
            bestillingsIds.map { mapOf("id" to it, "bestillingsbatchId" to bestillingsbatchId) },
        )
    }

    fun deleteProcessedBestillingBatch(
        tx: TransactionalSession,
        fnrList: List<String>,
        batchId: Long,
    ) = run {
        // language=SQL
        val sql =
            """
            DELETE FROM bestillinger
                WHERE bestillingsbatch_id = :bestillingsbatchId
                AND fnr = :fnr
            """.trimIndent()
        tx.batchPreparedNamedStatement(
            sql,
            fnrList.map { fnr ->
                mapOf(
                    "bestillingsbatchId" to batchId,
                    "fnr" to fnr,
                )
            },
        )
    }

    fun findByPersonIdAndInntektsaar(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
    ): Bestilling? {
        // language=SQL
        val sql =
            """
            SELECT * FROM bestillinger
            WHERE person_id = :personId AND inntektsaar = :inntektsaar
            """.trimIndent()
        return tx.single(
            queryOf(
                sql,
                mapOf(
                    "personId" to personId.value,
                    "inntektsaar" to inntektsaar,
                ),
            ),
            extractor = mapToBestilling,
        )
    }

    fun getAllBestillingsInBatch(
        tx: TransactionalSession,
        batchId: Long,
    ) = run {
        // language=SQL
        val sql =
            """
            SELECT * FROM bestillinger
            WHERE bestillingsbatch_id = :bestillingsbatchId
            """.trimIndent()
        tx.list(
            queryOf(
                sql,
                mapOf(
                    "bestillingsbatchId" to batchId,
                ),
            ),
            extractor = mapToBestilling,
        )
    }

    fun getEarliestUnsentBestillingTime(tx: TransactionalSession): Double {
        // language=SQL
        val sql =
            """
            SELECT EXTRACT(EPOCH FROM NOW() - COALESCE(MIN(oppdatert), NOW())) as earliest_oppdatert FROM bestillinger
            WHERE bestillingsbatch_id IS NULL
            """.trimIndent()
        return tx.single(
            queryOf(sql),
            extractor = { row -> row.double("earliest_oppdatert") },
        ) ?: error("Should always return something")
    }

    fun getEarliestSentBestillingTime(tx: TransactionalSession): Double {
        // language=SQL
        val sql =
            """
            SELECT EXTRACT(EPOCH FROM NOW() - COALESCE(MIN(oppdatert), NOW())) as earliest_oppdatert FROM bestillinger
            WHERE bestillingsbatch_id IS NOT NULL
            """.trimIndent()
        return tx.single(
            queryOf(sql),
            extractor = { row -> row.double("earliest_oppdatert") },
        ) ?: error("Should always return something")
    }

    fun hentResterendeBestillinger(
        tx: TransactionalSession,
        batchId: Long,
    ): List<PersonId> {
        // language=SQL
        val sql =
            """
            SELECT person_id FROM bestillinger
            WHERE bestillingsbatch_id = :bestillingsbatchId
            """.trimIndent()
        return tx.list(
            queryOf(
                sql,
                mapOf(
                    "bestillingsbatchId" to batchId,
                ),
            ),
            extractor = { row ->
                PersonId(row.long("person_id"))
            },
        )
    }

    val mapToBestilling: (Row) -> Bestilling = { row ->
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
