package no.nav.sokos.skattekort.utsending

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.SkattekortId

object UtsendingRepository {
    fun insertBatch(
        tx: TransactionalSession,
        utsendingList: List<Utsending>,
    ) = run {
        // language=SQL
        val sql =
            """
            INSERT INTO utsendinger (fnr, inntektsaar, forsystem, skattekort_id)
            VALUES (:fnr, :inntektsaar, :forsystem, :skattekortId)
            ON CONFLICT (fnr, inntektsaar, forsystem) DO NOTHING
            """.trimIndent()
        tx.batchPreparedNamedStatement(
            sql,
            utsendingList.map { utsending ->
                mapOf(
                    "fnr" to utsending.fnr.value,
                    "inntektsaar" to utsending.inntektsaar,
                    "forsystem" to utsending.forsystem.value,
                    "skattekortId" to utsending.skattekortId.value,
                )
            },
        )
    }.sum()

    fun insert(
        tx: TransactionalSession,
        utsending: Utsending,
    ): Int = insertBatch(tx, listOf(utsending))

    fun deleteBatch(
        tx: TransactionalSession,
        idList: List<UtsendingId>,
    ): Int {
        val idParamList = List(idList.size) { index -> ":id$index" }.joinToString(", ")
        // language=SQL
        val sql =
            """
            DELETE FROM utsendinger WHERE id IN ($idParamList)
            """.trimIndent()
        return tx.update(
            queryOf(
                sql,
                idList.mapIndexed { index, id -> "id$index" to id.value }.toMap(),
            ),
        )
    }

    fun getAllUtsendinger(
        tx: TransactionalSession,
        limit: Int? = null,
        fail_count: Int = 3,
    ): List<Utsending> {
        // language=SQL
        val sql =
            """
            SELECT * FROM utsendinger WHERE (:failCount = 0 OR fail_count < :failCount) ORDER BY id, fail_count LIMIT NULLIF(:limit, 0)  
            """.trimIndent()
        return tx.list(
            queryOf(
                sql,
                mapOf(
                    "limit" to (limit ?: 0),
                    "failCount" to fail_count,
                ),
            ),
            extractor = { row -> Utsending(row) },
        )
    }

    fun increaseFailCount(
        tx: TransactionalSession,
        failMessage: String,
        idList: List<UtsendingId>,
    ) = run {
        // language=SQL
        val sql =
            """
            UPDATE utsendinger SET
            fail_count = fail_count + 1,
            fail_message = :fail_message
            WHERE id = :id
            """.trimIndent()
        tx.batchPreparedNamedStatement(
            sql,
            idList.map { id ->
                mapOf(
                    "id" to id.value,
                    "fail_message" to failMessage,
                )
            },
        )
    }.sum()

    fun findByPersonIdAndInntektsaar(
        tx: TransactionalSession,
        fnr: Personidentifikator,
        inntektsaar: Int,
        forsystem: Forsystem,
    ): Utsending? {
        // language=SQL
        val sql =
            """
            SELECT id, fnr, forsystem, inntektsaar, opprettet, fail_count, fail_message, skattekort_id FROM utsendinger
            WHERE fnr = :fnr AND inntektsaar = :inntektsaar AND forsystem = :forsystem
            """.trimIndent()
        return tx.single(
            queryOf(
                sql,
                mapOf(
                    "fnr" to fnr.value,
                    "inntektsaar" to inntektsaar,
                    "forsystem" to forsystem.value,
                ),
            ),
            extractor = { row -> Utsending(row) },
        )
    }

    fun getSecondsSinceEarliestUnsentUtsending(tx: TransactionalSession): Double {
        // language=SQL
        val sql =
            """
            SELECT EXTRACT(EPOCH FROM NOW() - COALESCE(MIN(opprettet), NOW())) as earliest_opprettet FROM utsendinger
            """.trimIndent()
        return tx.single(
            queryOf(sql),
            extractor = { row -> row.double("earliest_opprettet") },
        ) ?: error("Should always return a number")
    }

    fun lagreBevis(
        tx: TransactionalSession,
        id: SkattekortId,
        forsystem: Forsystem,
        fnr: Personidentifikator,
        copybook: String,
    ) {
        // language=SQL
        val sql =
            """INSERT INTO bevis_sending
                |(skattekort_id, forsystem, fnr, sending) VALUES (:id, :forsystem, :fnr, :sending)
            """.trimMargin()
        tx.update(
            queryOf(
                sql,
                mapOf(
                    "id" to id.value,
                    "forsystem" to forsystem.value,
                    "fnr" to fnr.value,
                    "sending" to copybook,
                ),
            ),
        )
    }
}
