package no.nav.sokos.skattekort.module.person

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

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

    fun getAllFodseslsnummerByPersonId(
        tx: TransactionalSession,
        id: PersonId,
    ): List<Foedselsnummer> =
        tx.list(
            queryOf(
                """SELECT * from foedselsnumre 
                    WHERE person_id=:personIdent
                    ORDER BY gjelder_fom DESC
                """.trimMargin(),
                mapOf("personIdent" to id.value),
            ),
            { row ->
                Foedselsnummer(
                    id = FoedselsnummerId(row.long("id")),
                    personId = PersonId(row.long("person_id")),
                    gjelderFom = row.localDate("gjelder_fom").toKotlinLocalDate(),
                    fnr = Personidentifikator(row.string("fnr")),
                )
            },
        )

    fun findPersonIdByPersonidentifikator(
        tx: TransactionalSession,
        personidentifikatorList: List<String>,
    ): PersonId? =
        tx.single(
            queryOf(
                """
                SELECT person_id FROM foedselsnumre
                WHERE fnr = ANY(?)
                """.trimIndent(),
                personidentifikatorList.toTypedArray(),
            ),
            extractor = { row -> PersonId(row.long("person_id")) },
        )
}
