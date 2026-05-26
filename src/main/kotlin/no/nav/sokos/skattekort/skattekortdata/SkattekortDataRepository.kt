package no.nav.sokos.skattekort.skattekortdata

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

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
    ): Long? {
        @Language("PostgreSQL")
        val sql =
            """
            INSERT INTO skattekort_data (data_mottatt, inntektsaar, fnr, type)
            VALUES ((CAST (:dataMottatt AS JSON)), :inntektsaar, :fnr, :type)
            """.trimIndent()
        return tx.run(
            queryOf(
                sql,
                mapOf(
                    "dataMottatt" to dataMottatt,
                    "inntektsaar" to inntektsaar,
                    "fnr" to fnr,
                    "type" to type.name,
                ),
            ).asUpdateAndReturnGeneratedKey,
        )
    }

    fun updateSkattekortId(
        tx: TransactionalSession,
        id: Long,
        skattekortId: Long,
    ) {
        @Language("PostgreSQL")
        val sql =
            """
            UPDATE skattekort_data SET skattekort_id = :skattekortId WHERE id = :id
            """.trimIndent()
        tx.update(
            queryOf(
                sql,
                mapOf(
                    "skattekortId" to skattekortId,
                    "id" to id,
                ),
            ),
        )
    }

    fun getUnprocessedSkattekortData(tx: TransactionalSession): List<SkattekortData> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT id, inntektsaar, data_mottatt, opprettet, fnr, skattekort_id, type FROM skattekort_data
            WHERE skattekort_id is null
            """.trimIndent()
        return tx.list(
            queryOf(sql),
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
}
