package no.nav.sokos.skattekort.person

import kotliquery.TransactionalSession
import kotliquery.queryOf

class PersonRepository {
    fun findOrCreateByOffNr(
        tx: TransactionalSession,
        offNr: String,
        grunn: String, // Årsak for å lage aktør, hvis nødvendig
    ): Pair<PersonId, Boolean> = (internalFindByOffNr(tx, offNr)?.id?.let { aId -> Pair(aId, false) }) ?: Pair(createByOffnr(tx, offNr, grunn), true)

    private fun createByOffnr(
        tx: TransactionalSession,
        offNr: String,
        grunn: String,
    ): PersonId {
        val personId =
            PersonId(
                tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """INSERT INTO person (flagget) VALUES (:flagget)""".trimMargin(),
                        mapOf("flagget" to false),
                    ),
                )!!,
            )

        tx.updateAndReturnGeneratedKey(
            queryOf(
                """INSERT INTO person_audit
            |(person_id, tag, bruker_id, informasjon)
            | VALUES (:person_id, :tag, :bruker, :informasjon)
                """.trimMargin(),
                mapOf(
                    "person_id" to personId.id,
                    "tag" to AuditTag.OPPRETTET_AKTOER.name,
                    "bruker" to "system",
                    "informasjon" to "Opprettet aktør. Grunn: $grunn",
                ),
            ),
        )
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """INSERT INTO person_fnr
                    |(person_id, fnr)
                    |VALUES (:person_id, :fnr)
                """.trimMargin(),
                mapOf(
                    "person_id" to personId.id,
                    "fnr" to offNr,
                ),
            ),
        )
        return personId
    }

    private fun internalFindByOffNr(
        tx: TransactionalSession,
        personIdent: String,
    ): Person? =
        tx.run(
            queryOf(
                """SELECT a.* FROM person a 
                            |LEFT JOIN person_fnr ao ON a.id = ao.person_id 
                            |WHERE ao.fnr = :aktident AND gjelder_fom <= now()
                """.trimMargin(),
                mapOf("aktident" to personIdent),
            ).map { row ->
                val id = PersonId(row.long("id"))
                Person(
                    id = id,
                    flagget = row.boolean("flagget"),
                    fnr = findOffList(tx, id),
                )
            }.asSingle,
        )

    private fun findOffList(
        tx: TransactionalSession,
        id: PersonId,
    ): List<Foedselsnummer> =
        tx
            .run(
                queryOf(
                    """SELECT id, fnr, gjelder_fom 
                    |FROM person_fnr 
                    |WHERE person_id=:aktid 
                    |ORDER BY gjelder_fom DESC
                    """.trimMargin(),
                    mapOf<String, Any>("aktid" to id.id),
                ).map { row ->
                    Foedselsnummer(
                        id = FoedselsnummerId(row.long("id")),
                        fnr = row.string("fnr"),
                        gjelderFom = row.localDate("gjelder_fom"),
                    )
                }.asList,
            )

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
                """SELECT * 
                    |FROM person 
                """.trimMargin() + where +
                    """ ORDER BY id ASC LIMIT :count""",
                params,
            ).map { row ->
                val id = PersonId(row.long("id"))
                Person(
                    id = id,
                    flagget = row.boolean("flagget"),
                    fnr = findOffNr(tx, id),
                )
            }.asList,
        )
    }

    private fun findOffNr(
        tx: TransactionalSession,
        id: PersonId,
    ): List<Foedselsnummer> =
        tx
            .run(
                queryOf(
                    """SELECT id, person_ident, gjelder_fom from person_fnr 
                    |WHERE person_id=:aktid 
                    |ORDER BY gjelder_fom DESC
                    """.trimMargin(),
                    mapOf<String, Any>("aktid" to id.id),
                ).map { row ->
                    Foedselsnummer(
                        FoedselsnummerId(row.long("id")),
                        gjelderFom = row.localDate("gjelder_fom"),
                        fnr = row.string("person_ident"),
                    )
                }.asList,
            )
}
