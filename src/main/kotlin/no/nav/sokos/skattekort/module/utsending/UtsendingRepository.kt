package no.nav.sokos.skattekort.module.utsending

import kotliquery.TransactionalSession
import kotliquery.queryOf

object UtsendingRepository {
    fun insert(
        tx: TransactionalSession,
        utsending: Utsending,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                INSERT INTO utsendinger (abonnement_id, fnr, inntektsaar, forsystem)
                VALUES (:abonnementId, :fnr, :inntektsaar, :forsystem)
                """.trimIndent(),
                mapOf(
                    "abonnementId" to utsending.abonnementId.value,
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
                """SELECT * FROM utsendinger FOR UPDATE""".trimIndent(),
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
}
