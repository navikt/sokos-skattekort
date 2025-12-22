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

    fun findPersonIdByFnr(
        tx: TransactionalSession,
        fnr: Personidentifikator,
    ): PersonId? =
        tx.single(
            queryOf(
                """
                    |SELECT distinct p.id 
                    |FROM personer p INNER JOIN foedselsnumre f ON p.id = f.person_id 
                    |WHERE f.fnr = :fnr;
                """.trimMargin(),
                mapOf("fnr" to fnr.value),
            ),
        ) { row -> PersonId(row.long("id")) }

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
                    |FROM personer p JOIN foedselsnumre pf ON p.id = pf.person_id 
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
    ): Long? =
        tx.single(
            queryOf(
                """
            |WITH existing_foedselsnummer AS (
            |   SELECT person_id FROM foedselsnumre WHERE fnr = :fnr
            |),
            |inserted_person AS (
            |   INSERT INTO personer (flagget)
            |   SELECT :flagget
            |   WHERE NOT EXISTS (SELECT 1 FROM existing_foedselsnummer)
            |   RETURNING id AS person_id
            |),
            |resolved_person AS (
            |   SELECT COALESCE(
            |     (SELECT person_id FROM inserted_person),
            |     (SELECT person_id FROM existing_foedselsnummer)
            |   ) AS person_id
            |),
            |upserted_foedselsnummer AS (
            |   INSERT INTO foedselsnumre (person_id, gjelder_fom, fnr)
            |   SELECT person_id, :gjelderFom, :fnr
            |   FROM resolved_person
            |   ON CONFLICT (fnr) DO NOTHING
            |   RETURNING person_id
            |),
            |audit_insert AS (
            |   INSERT INTO person_audit(person_id, bruker_id, opprettet, tag, informasjon)
            |   SELECT person_id, :brukerId, now(), :tag, :informasjon
            |   FROM inserted_person
            |   RETURNING person_id
            |)
            |SELECT person_id FROM resolved_person;
                """.trimMargin(),
                mapOf(
                    "fnr" to fnr.value,
                    "flagget" to false,
                    "gjelderFom" to gjelderFom,
                    "brukerId" to (brukerId ?: AUDIT_SYSTEM),
                    "tag" to AuditTag.OPPRETTET_PERSON.name,
                    "informasjon" to informasjon,
                ),
            ),
        ) { row -> row.long("person_id") }

    fun flaggPerson(
        tx: TransactionalSession,
        personId: PersonId,
    ) = tx.execute(
        queryOf(
            """
                    |UPDATE personer 
                    |SET flagget = true
                    |WHERE id = :personId
            """.trimMargin(),
            mapOf("personId" to personId.value),
        ),
    )

    private val mapToPerson: (Row) -> Person = { row ->
        Person(
            id = PersonId(row.long("person_id")),
            flagget = row.boolean("flagget"),
            foedselsnummer =
                Foedselsnummer(
                    id = FoedselsnummerId(row.long("foedselsnummer_id")),
                    personId = PersonId(row.long("person_id")),
                    fnr = Personidentifikator(row.string("fnr")),
                    gjelderFom = row.localDate("gjelder_fom").toKotlinLocalDate(),
                ),
        )
    }
}
