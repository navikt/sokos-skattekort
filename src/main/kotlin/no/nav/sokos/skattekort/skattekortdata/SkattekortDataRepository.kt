package no.nav.sokos.skattekort.skattekortdata

import kotliquery.TransactionalSession
import kotliquery.queryOf

object SkattekortDataRepository {
    fun insert(
        tx: TransactionalSession,
        dataMottatt: String,
        inntektsaar: Int,
        fnr: String,
    ): Long? =
        tx.run(
            queryOf(
                """
                INSERT INTO skattekort_data (data_mottatt, inntektsaar, fnr)
                VALUES ((CAST (:dataMottatt AS JSON)), :inntektsaar, :fnr)
                """.trimIndent(),
                mapOf(
                    "dataMottatt" to dataMottatt,
                    "inntektsaar" to inntektsaar,
                    "fnr" to fnr,
                ),
            ).asUpdateAndReturnGeneratedKey,
        )

    fun updateSkattekortId(
        tx: TransactionalSession,
        id: Long,
        skattekortId: Long,
    ) {
        tx.update(
            queryOf(
                """
                UPDATE skattekort_data SET skattekort_id = :skattekortId WHERE id = :id
                """.trimIndent(),
                mapOf(
                    "skattekortId" to skattekortId,
                    "id" to id,
                ),
            ),
        )
    }

    fun getUnprocessedSkattekortData(tx: TransactionalSession): List<Pair<Long, String>> =
        tx.list(
            queryOf(
                """
                SELECT id, data_mottatt FROM skattekort_data 
                WHERE skattekort_id is null
                """.trimIndent(),
            ),
            extractor = { row ->
                Pair(row.long("id"), row.string("data_mottatt"))
            },
        )
}
