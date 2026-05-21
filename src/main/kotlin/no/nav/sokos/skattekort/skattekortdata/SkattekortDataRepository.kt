package no.nav.sokos.skattekort.skattekortdata

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.SkattekortId
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType

object SkattekortDataRepository {
    fun insert(
        tx: TransactionalSession,
        dataMottatt: String,
        inntektsaar: Int,
        fnr: String,
        type: BestillingsbatchType,
    ): Long? =
        tx.run(
            queryOf(
                """
                INSERT INTO skattekort_data (data_mottatt, inntektsaar, fnr, type)
                VALUES ((CAST (:dataMottatt AS JSON)), :inntektsaar, :fnr, :type)
                """.trimIndent(),
                mapOf(
                    "dataMottatt" to dataMottatt,
                    "inntektsaar" to inntektsaar,
                    "fnr" to fnr,
                    "type" to type.name,
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

    fun getUnprocessedSkattekortData(tx: TransactionalSession): List<SkattekortData> =
        tx.list(
            queryOf(
                """
                SELECT id, inntektsaar, data_mottatt, opprettet, fnr, skattekort_id, type FROM skattekort_data 
                WHERE skattekort_id is null
                """.trimIndent(),
            ),
            extractor = { row ->
                SkattekortData(
                    id = SkattekortDataId(row.long("id")),
                    inntektsaar = row.int("inntektsaar"),
                    dataMottatt = row.string("data_mottatt"),
                    opprettet = row.instant("opprettet"),
                    fnr = Personidentifikator(row.string("fnr")),
                    skattekortId = row.longOrNull("skattekort_id")?.let { SkattekortId(it) },
                    type = row.stringOrNull("type")?.let { BestillingsbatchType.valueOf(it) },
                )
            },
        )
}
