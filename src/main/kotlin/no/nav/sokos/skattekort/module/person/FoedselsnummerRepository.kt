package no.nav.sokos.skattekort.module.person

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
    ): PersonId? =
        tx.single(
            queryOf(
                """
                SELECT person_id FROM foedselsnumre
                WHERE fnr = ANY(?)
                """.trimIndent(),
                fnrList.toTypedArray(),
            ),
            extractor = { row -> PersonId(row.long("person_id")) },
        )
}
