package no.nav.sokos.skattekort.person

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

object FoedselsnummerRepository {
    fun insert(
        tx: TransactionalSession,
        foedselsnummer: Foedselsnummer,
    ): Long? {
        @Language("PostgreSQL")
        val sql =
            """
            INSERT INTO foedselsnumre (person_id, gjelder_fom, fnr)
            VALUES (:personId, :gjelderFom, :fnr)
            """.trimIndent()
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                sql,
                mapOf(
                    "personId" to foedselsnummer.personId?.value,
                    "gjelderFom" to foedselsnummer.gjelderFom,
                    "fnr" to foedselsnummer.fnr.value,
                ),
            ),
        )
    }

    fun insertByExistingFnr(
        tx: TransactionalSession,
        fnr: String,
        existingFnr: String,
    ): Long? {
        @Language("PostgreSQL")
        val sql =
            """
            INSERT INTO foedselsnumre (person_id, gjelder_fom, fnr)
            SELECT person_id, gjelder_fom - INTERVAL '1 day', :fnr FROM foedselsnumre WHERE fnr = :existingFnr
            """.trimIndent()
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                sql,
                mapOf(
                    "fnr" to fnr,
                    "existingFnr" to existingFnr,
                ),
            ),
        )
    }

    fun findPersonIdByFnrList(
        tx: TransactionalSession,
        fnrList: List<String>,
    ): Map<String, PersonId?> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT person_id, fnr FROM foedselsnumre
            WHERE fnr = ANY(?)
            """.trimIndent()
        val resultMap =
            tx
                .list(
                    queryOf(
                        sql,
                        fnrList.toTypedArray(),
                    ),
                    extractor = { row ->
                        row.string("fnr") to PersonId(row.long("person_id"))
                    },
                ).toMap()
        return fnrList.associateWith { fnr -> resultMap[fnr] }
    }
}
