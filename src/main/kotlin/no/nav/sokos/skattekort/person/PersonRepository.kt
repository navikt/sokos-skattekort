package no.nav.sokos.skattekort.person

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

class PersonRepository {
    fun list(
        tx: TransactionalSession,
        count: Int,
        startId: String?,
    ): List<Person> {
        val where =
            listOfNotNull(
                startId?.let { "id > :startId" },
            ).joinToString(" AND ").takeIf { it.isNotEmpty() }?.let { " WHERE $it" } ?: ""
        val params =
            listOfNotNull(
                "count" to count + 1,
                startId?.let { "startid" to it },
            ).toMap()
        return tx.run(
            queryOf(
                """SELECT * FROM person""".trimMargin() + where +
                    """ ORDER BY id ASC LIMIT :count""",
                params,
            ).map { row ->
                val id = PersonId(row.long("id"))
                Person(
                    id = id,
                    flagget = row.boolean("flagget"),
                    fnrs = findAllFnrForPerson(tx, id),
                )
            }.asList,
        )
    }

    fun createNewPersonId(tx: TransactionalSession): PersonId =
        PersonId(
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """INSERT INTO person (flagget) VALUES (:flagget)""".trimMargin(),
                    mapOf("flagget" to false),
                ),
            )!!,
        )

    fun createNewFnr(
        tx: TransactionalSession,
        personId: PersonId,
        fnr: String,
    ): FoedselsnummerId =
        tx
            .updateAndReturnGeneratedKey(
                queryOf(
                    """INSERT INTO person_fnr
                    |(person_id, fnr)
                    |VALUES (:person_id, :fnr)
                    """.trimMargin(),
                    mapOf(
                        "person_id" to personId.id,
                        "fnr" to fnr,
                    ),
                ),
            ).let { it ?: throw RuntimeException("Failed to insert fnr for person ${personId.id}") }
            .let { FoedselsnummerId(it) }

    fun audit(
        tx: TransactionalSession,
        tag: AuditTag,
        personId: PersonId,
        informasjon: String,
    ) = tx.updateAndReturnGeneratedKey(
        queryOf(
            """INSERT INTO person_audit
            |(person_id, tag, bruker_id, informasjon)
            | VALUES (:person_id, :tag, :bruker, :informasjon)
            """.trimMargin(),
            mapOf(
                "person_id" to personId.id,
                "tag" to tag.name,
                "bruker" to "system",
                "informasjon" to informasjon,
            ),
        ),
    )

    fun internalFindPersonByFnr(
        tx: TransactionalSession,
        personIdent: String,
    ): Person? =
        tx.run(
            queryOf(
                """SELECT a.* FROM person a 
                            |LEFT JOIN person_fnr ao ON a.id = ao.person_id 
                            |WHERE ao.fnr = :personIdent AND gjelder_fom <= now()
                """.trimMargin(),
                mapOf("personIdent" to personIdent),
            ).map { row -> mapPerson(tx, row) }.asSingle,
        )

    fun findPersonById(
        tx: TransactionalSession,
        personId: PersonId,
    ): Person =
        tx.run(
            queryOf(
                """SELECT * FROM person WHERE id = :personId""".trimMargin(),
                mapOf("personId" to personId.id),
            ).map { row -> mapPerson(tx, row) }.asSingle,
        )!!

    private fun mapPerson(
        tx: TransactionalSession,
        row: Row,
    ): Person {
        val id = PersonId(row.long("id"))
        return Person(
            id = id,
            flagget = row.boolean("flagget"),
            fnrs = findAllFnrForPerson(tx, id),
        )
    }

    private fun findAllFnrForPerson(
        tx: TransactionalSession,
        id: PersonId,
    ): List<Foedselsnummer> =
        tx
            .run(
                queryOf(
                    """SELECT id, fnr, gjelder_fom from person_fnr 
                    |WHERE person_id=:personIdent
                    |ORDER BY gjelder_fom DESC
                    """.trimMargin(),
                    mapOf<String, Any>("personIdent" to id.id),
                ).map { row ->
                    Foedselsnummer(
                        id = FoedselsnummerId(row.long("id")),
                        fnr = row.string("fnr"),
                        gjelderFom = row.localDate("gjelder_fom"),
                    )
                }.asList,
            )
}
