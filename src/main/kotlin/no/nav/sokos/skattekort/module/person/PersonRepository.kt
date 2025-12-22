package no.nav.sokos.skattekort.module.person

import java.time.LocalDate

import kotlinx.datetime.toKotlinLocalDate

import kotliquery.Row
import kotliquery.Session
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
        session: Session,
        fnr: Personidentifikator,
    ): PersonId? =
        session.single(
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
        session: Session,
        personId: PersonId,
    ): Person =
        session.single(
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
        session: Session,
        fnr: Personidentifikator,
    ): Person? =
        session.single(
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
    ): Long? {
        val existingPersonId =
            tx.single(
                queryOf(
                    """
            |SELECT person_id FROM foedselsnumre WHERE fnr = :fnr
                    """.trimMargin(),
                    mapOf("fnr" to fnr.value),
                ),
            ) { row -> row.long("person_id") }

        if (existingPersonId != null) {
            return existingPersonId
        }

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
