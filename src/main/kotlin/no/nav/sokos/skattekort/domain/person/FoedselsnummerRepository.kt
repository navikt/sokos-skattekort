package no.nav.sokos.skattekort.domain.person

import kotlinx.datetime.toKotlinLocalDate

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.util.SQLUtils.asMap

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
                foedselsnummer.asMap(),
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
}
