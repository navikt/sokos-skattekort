package no.nav.sokos.skattekort.domain.utsending

import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf

object UtsendingRepository {
    fun insertBatch(
        tx: TransactionalSession,
        utsendingList: List<Utsending>,
    ): List<Int> =
        tx
            .batchPreparedStatement(
                """
                INSERT INTO utsendinger (abonnement_id, fnr, inntektsaar, forsystem)
                VALUES (:abonnementId, :fnr, :inntektsaar, :forsystem)
                """.trimIndent(),
                utsendingList.map {
                    listOf(
                        "abonnementId" to it.abonnementId.value,
                        "fnr" to it.fnr.value,
                        "innteksaar" to it.inntektsaar,
                        "forsystem" to it.forsystem.kode,
                    )
                },
            ).orEmpty()

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

    fun getAllUtsendinger(session: Session): List<Utsending> =
        session.list(
            queryOf(
                """
                SELECT id, abonnement_id, fnr, inntektsaar, forsystem, opprettet
                FROM utsendinger
                """.trimIndent(),
            ),
            extractor = { row -> Utsending(row) },
        )
}
