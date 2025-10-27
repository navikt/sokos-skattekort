package no.nav.sokos.skattekort.module.person

import java.time.LocalDate

import kotlinx.datetime.toKotlinLocalDate

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

object PersonRepository {
    fun getAllPersonById(
        tx: TransactionalSession,
        count: Int,
        startId: String?,
    ): List<Person> {
        val where =
            listOfNotNull(
                startId?.let { "p.id > :startId" },
            ).joinToString(" AND ").takeIf { it.isNotEmpty() }?.let { " WHERE $it" } ?: ""
        return tx.list(
            queryOf(
                """
                    |SELECT p.id as person_id, p.flagget, pf.id as foedselsnummer_id, pf.gjelder_fom, pf.fnr
                    |FROM personer p 
                    |LEFT JOIN LATERAL (
                    |   SELECT id, gjelder_fom, fnr
                    |   FROM foedselsnumre
                    |   WHERE person_id = p.id
                    |   ORDER BY gjelder_fom DESC, id DESC
                    |   LIMIT 1
                    |) pf ON TRUE
                    |$where 
                    |ORDER BY p.id ASC 
                    |LIMIT :count
                """.trimMargin(),
                listOfNotNull(
                    "count" to count + 1,
                    startId?.let { "startid" to it },
                ).toMap(),
            ),
            extractor = mapToPerson,
        )
    }

    fun findPersonById(
        tx: TransactionalSession,
        personId: PersonId,
    ): Person =
        tx.single(
            queryOf(
                """
                    |SELECT p.id as person_id, p.flagget, pf.id as foedselsnummer_id, pf.gjelder_fom, pf.fnr
                    |FROM personer p 
                    |LEFT JOIN LATERAL (
                    |   SELECT id, gjelder_fom, fnr
                    |   FROM foedselsnumre
                    |   WHERE person_id = p.id
                    |   ORDER BY gjelder_fom DESC, id DESC
                    |   LIMIT 1
                    |) pf ON TRUE
                    |WHERE p.id = :personId
                """.trimMargin(),
                mapOf("personId" to personId.value),
            ),
            extractor = mapToPerson,
        )!!

    fun findPersonByFnr(
        tx: TransactionalSession,
        fnr: Personidentifikator,
    ): Person? =
        tx.single(
            queryOf(
                """
                    |SELECT p.id as person_id, p.flagget, pf.id as foedselsnummer_id, pf.gjelder_fom, pf.fnr
                    |FROM personer p 
                    |LEFT JOIN LATERAL (
                    |   SELECT id, gjelder_fom, fnr
                    |   FROM foedselsnumre
                    |   WHERE person_id = p.id
                    |   ORDER BY gjelder_fom DESC, id DESC
                    |   LIMIT 1
                    |) pf ON TRUE 
                    |WHERE pf.fnr = :fnr
                """.trimMargin(),
                mapOf("fnr" to fnr.value),
            ),
            extractor = mapToPerson,
        )

    fun insert(
        tx: TransactionalSession,
        fnr: Personidentifikator,
        gjelderFom: LocalDate,
        informasjon: String,
        brukerId: String? = null,
    ): Long? {
        val personId =
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    |INSERT INTO personer (flagget) VALUES (:flagget)
                    """.trimMargin(),
                    mapOf("flagget" to false),
                ),
            )

        tx.execute(
            queryOf(
                """
                    |INSERT INTO foedselsnumre (person_id, gjelder_fom, fnr)
                    |    VALUES (:personId, :gjelderFom, :fnr);
                    |    
                    |INSERT INTO person_audit(person_id, bruker_id, opprettet, tag, informasjon)
                    |    VALUES (:personId, :brukerId, now(), :tag, :informasjon);
                """.trimMargin(),
                mapOf(
                    "personId" to personId,
                    "gjelderFom" to gjelderFom,
                    "fnr" to fnr.value,
                    "brukerId" to (brukerId ?: AUDIT_SYSTEM),
                    "tag" to AuditTag.OPPRETTET_PERSON.name,
                    "informasjon" to informasjon,
                ),
            ),
        )
        return personId
    }

    private val mapToPerson: (Row) -> Person = { row ->
        Person(
            id = PersonId(row.long("person_id").toLong()),
            flagget = row.boolean("flagget"),
            foedselsnummer =
                Foedselsnummer(
                    id = FoedselsnummerId(row.long("foedselsnummer_id").toLong()),
                    personId = PersonId(row.long("person_id").toLong()),
                    fnr = Personidentifikator(row.string("fnr")),
                    gjelderFom = row.localDate("gjelder_fom").toKotlinLocalDate(),
                ),
        )
    }
}
