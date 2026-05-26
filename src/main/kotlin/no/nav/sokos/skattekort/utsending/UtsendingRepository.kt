package no.nav.sokos.skattekort.utsending

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.SkattekortId

object UtsendingRepository {
    fun insert(
        tx: TransactionalSession,
        utsending: Utsending,
    ): Long? {
        @Language("PostgreSQL")
        val sql =
            """
            INSERT INTO utsendinger (fnr, inntektsaar, forsystem)
            VALUES (:fnr, :inntektsaar, :forsystem)
            ON CONFLICT (fnr, inntektsaar, forsystem) DO NOTHING
            """.trimIndent()
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                sql,
                mapOf(
                    "fnr" to utsending.fnr.value,
                    "inntektsaar" to utsending.inntektsaar,
                    "forsystem" to utsending.forsystem.value,
                ),
            ),
        )
    }

    fun delete(
        tx: TransactionalSession,
        id: UtsendingId,
    ) {
        @Language("PostgreSQL")
        val sql =
            "DELETE FROM utsendinger WHERE id = :id".trimIndent()
        tx.update(
            queryOf(
                sql,
                mapOf("id" to id.value),
            ),
        )
    }

    fun getAllUtsendinger(tx: TransactionalSession): List<Utsending> {
        @Language("PostgreSQL")
        val sql =
            "SELECT * FROM utsendinger ORDER BY ID".trimIndent()
        return tx.list(
            queryOf(sql),
            extractor = { row -> Utsending(row) },
        )
    }

    fun increaseFailCount(
        tx: TransactionalSession,
        maybeId: UtsendingId?,
        failMessage: String,
    ) {
        maybeId?.let { id ->
            @Language("PostgreSQL")
            val sql =
                """
                UPDATE utsendinger SET
                fail_count = fail_count + 1,
                fail_message = :fail_message
                WHERE id = :id
                """.trimIndent()
            tx.update(
                queryOf(
                    sql,
                    mapOf(
                        "id" to id.value,
                        "fail_message" to failMessage,
                    ),
                ),
            )
        }
    }

    fun findByPersonIdAndInntektsaar(
        tx: TransactionalSession,
        fnr: Personidentifikator,
        inntektsaar: Int,
        forsystem: Forsystem,
    ): Utsending? {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT id, fnr, forsystem, inntektsaar, opprettet, fail_count, fail_message FROM utsendinger
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
        @Language("PostgreSQL")
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
        @Language("PostgreSQL")
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

    fun slettGamleBevis(tx: TransactionalSession) {
        @Language("PostgreSQL")
        val sql =
            "DELETE FROM bevis_sending WHERE opprettet < now() - interval '7 days'"
        tx.update(
            queryOf(sql),
        )
    }
}
