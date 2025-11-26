package no.nav.sokos.skattekort.module.utsending

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.person.Personidentifikator

object UtsendingRepository {
    fun insert(
        tx: TransactionalSession,
        utsending: Utsending,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                INSERT INTO utsendinger (fnr, inntektsaar, forsystem)
                VALUES (:fnr, :inntektsaar, :forsystem)
                """.trimIndent(),
                mapOf(
                    "fnr" to utsending.fnr.value,
                    "inntektsaar" to utsending.inntektsaar,
                    "forsystem" to utsending.forsystem.value,
                ),
            ),
        )

    fun delete(
        tx: TransactionalSession,
        id: UtsendingId,
    ) {
        tx.update(
            queryOf(
                """DELETE FROM utsendinger WHERE id = :id""".trimIndent(),
                mapOf("id" to id.value),
            ),
        )
    }

    fun getAllUtsendinger(tx: TransactionalSession): List<Utsending> =
        tx.list(
            queryOf(
                """SELECT * FROM utsendinger""".trimIndent(),
            ),
            extractor = { row -> Utsending(row) },
        )

    fun increaseFailCount(
        tx: TransactionalSession,
        maybeId: UtsendingId?,
        failMessage: String,
    ) {
        maybeId?.let { id ->
            tx.update(
                queryOf(
                    """
                    UPDATE utsendinger SET
                    fail_count = fail_count + 1,
                    fail_message = :fail_message
                    WHERE id = :id
                    """.trimIndent(),
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
    ): Utsending? =
        tx.single(
            queryOf(
                """
                SELECT id, fnr, forsystem, inntektsaar, opprettet, fail_count, fail_message FROM utsendinger 
                WHERE fnr = :fnr AND inntektsaar = :inntektsaar AND forsystem = :forsystem
                """.trimIndent(),
                mapOf(
                    "fnr" to fnr.value,
                    "inntektsaar" to inntektsaar,
                    "forsystem" to forsystem.value,
                ),
            ),
            extractor = { row -> Utsending(row) },
        )

    fun getSecondsSinceEarliestUnsentUtsending(tx: TransactionalSession): Double =
        tx.single(
            queryOf(
                """
                EXTRACT(EPOCH FROM NOW() - COALESCE(MIN(oppdatert), NOW())) as earliest_oppdatert FROM utsendinger
                """.trimIndent(),
            ),
            extractor = { row -> row.double("earliest_opprettet") },
        ) ?: error("Should always return a number")
}
