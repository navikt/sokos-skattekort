package no.nav.sokos.skattekort.skattekortkonvertering

import kotliquery.TransactionalSession
import kotliquery.queryOf

object SkattekortDataRepository {
    fun insert(
        tx: TransactionalSession,
        dataMottatt: String,
        inntektsaar: Int,
        fnr: String,
    ) {
        tx.update(
            queryOf(
                """
                INSERT INTO skattekort_data (data_mottatt, inntektsaar, fnr)
                VALUES (:dataMottatt, :inntektsaar, :fnr)
                """.trimIndent(),
                mapOf(
                    "dataMottatt" to dataMottatt,
                    "inntektsaar" to inntektsaar,
                    "fnr" to fnr,
                ),
            ),
        )
    }

    fun getUnprocessedSkattekortData(tx: TransactionalSession): List<String> =
        tx.list(
            queryOf(
                """
                SELECT data_mottatt FROM skattekort_data 
                WHERE skattekort_id is null
                """.trimIndent(),
            ),
            extractor = { row ->
                row.string("data_mottatt")
            },
        )

}
