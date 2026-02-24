package no.nav.sokos.skattekort.person

import kotlinx.datetime.toJavaLocalDate

import kotliquery.TransactionalSession
import kotliquery.queryOf

object FoedselsnummerRepository {
    fun insert(
        tx: TransactionalSession,
        foedselsnummer: Foedselsnummer,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                INSERT INTO foedselsnumre (person_id, gjelder_fom, fnr) 
                VALUES (:personId, :gjelderFom, :fnr)
                """.trimIndent(),
                mapOf(
                    "personId" to foedselsnummer.personId?.value,
                    "gjelderFom" to foedselsnummer.gjelderFom.toJavaLocalDate(),
                    "fnr" to foedselsnummer.fnr.value,
                ),
            ),
        )

    fun findPersonIdByFnrList(
        tx: TransactionalSession,
        fnrList: List<String>,
    ): Map<String, PersonId?> {
        val resultMap =
            tx
                .list(
                    queryOf(
                        """
                        SELECT person_id, fnr FROM foedselsnumre
                        WHERE fnr = ANY(?)
                        """.trimIndent(),
                        fnrList.toTypedArray(),
                    ),
                    extractor = { row ->
                        row.string("fnr") to PersonId(row.long("person_id"))
                    },
                ).toMap()
        return fnrList.associateWith { fnr -> resultMap[fnr] }
    }
}
