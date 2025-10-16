package no.nav.sokos.skattekort.domain.utsending

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
                    "forsystem" to utsending.forsystem.kode,
                ),
            ),
        )

    fun delete(
        tx: TransactionalSession,
        id: UtsendingId,
    ) {
        tx.execute(
            queryOf(
                """
                DELETE FROM utsendinger WHERE id = :id
                """.trimIndent(),
                listOf("id" to id.value),
            ),
        )
    }

    fun getAllUtsendinger(tx: TransactionalSession): List<Utsending> =
        tx.list(
            queryOf(
                """
                SELECT id, abonnement_id, fnr, inntektsaar, forsystem, opprettet
                FROM utsendinger
                """.trimIndent(),
            ),
            extractor = { row -> Utsending(row) },
        )
}
