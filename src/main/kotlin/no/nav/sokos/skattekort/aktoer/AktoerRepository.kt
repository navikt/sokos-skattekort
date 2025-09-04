package no.nav.sokos.skattekort.aktoer

import kotliquery.TransactionalSession
import kotliquery.queryOf

class AktoerRepository {
    fun findOrCreateByOffNr(
        tx: TransactionalSession,
        offNr: String,
        grunn: String, // Årsak for å lage aktør, hvis nødvendig
    ): Pair<AktoerId, Boolean> = (internalFindByOffNr(tx, offNr)?.id?.let { aId -> Pair(aId, false) }) ?: Pair(createByOffnr(tx, offNr, grunn), true)

    private fun createByOffnr(
        tx: TransactionalSession,
        offNr: String,
        grunn: String,
    ): AktoerId {
        val aktoerId =
            AktoerId(
                tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """INSERT INTO aktoer (flagget) VALUES (:flagget)""".trimMargin(),
                        mapOf("flagget" to false),
                    ),
                )!!,
            )

        tx.updateAndReturnGeneratedKey(
            queryOf(
                """INSERT INTO aktoer_audit
            |(aktoer_id, tag, bruker, informasjon)
            | VALUES (:aktoer_id, :tag, :bruker, :informasjon)
                """.trimMargin(),
                mapOf(
                    "aktoer_id" to aktoerId.id,
                    "tag" to AuditTag.OPPRETTET_AKTOER.name,
                    "bruker" to "system",
                    "informasjon" to "Opprettet aktør. Grunn: $grunn",
                ),
            ),
        )
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """INSERT INTO aktoer_offnr
                    |(aktoer_id, aktoer_ident)
                    |VALUES (:aktoer_id, :aktoer_ident)
                """.trimMargin(),
                mapOf(
                    "aktoer_id" to aktoerId.id,
                    "aktoer_ident" to offNr,
                ),
            ),
        )
        return aktoerId
    }

    private fun internalFindByOffNr(
        tx: TransactionalSession,
        aktoerIdent: String,
    ): Aktoer? =
        tx.run(
            queryOf(
                """SELECT a.* FROM aktoer a 
                            |LEFT JOIN aktoer_offnr ao ON a.id = ao.aktoer_id 
                            |WHERE ao.aktoer_ident = :aktident AND gjelder_fom <= now()
                """.trimMargin(),
                mapOf("aktident" to aktoerIdent),
            ).map { row ->
                val id = AktoerId(row.long("id"))
                Aktoer(
                    id = id,
                    flagget = row.boolean("flagget"),
                    offNr = findOffList(tx, id),
                )
            }.asSingle,
        )

    private fun findOffList(
        tx: TransactionalSession,
        id: AktoerId,
    ): List<OffNr> =
        tx
            .run(
                queryOf(
                    """SELECT id, aktoer_ident, gjelder_fom 
                    |FROM aktoer_offnr 
                    |WHERE aktoer_id=:aktid 
                    |ORDER BY gjelder_fom DESC
                    """.trimMargin(),
                    mapOf<String, Any>("aktid" to id.id),
                ).map { row ->
                    OffNr(
                        id = OffNrId(row.long("id")),
                        aktoerIdent = row.string("aktoer_ident"),
                        gjelderFom = row.localDate("gjelder_fom"),
                    )
                }.asList,
            )

    fun list(
        tx: TransactionalSession,
        count: Int,
        startId: String?,
    ): List<Aktoer> {
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
                    |FROM aktoer 
                """.trimMargin() + where +
                    """ ORDER BY id ASC LIMIT :count""",
                params,
            ).map { row ->
                val id = AktoerId(row.long("id"))
                Aktoer(
                    id = id,
                    flagget = row.boolean("flagget"),
                    offNr = findOffNr(tx, id),
                )
            }.asList,
        )
    }

    private fun findOffNr(
        tx: TransactionalSession,
        id: AktoerId,
    ): List<OffNr> =
        tx
            .run(
                queryOf(
                    """SELECT id, aktoer_ident, gjelder_fom from aktoer_offnr 
                    |WHERE aktoer_id=:aktid 
                    |ORDER BY gjelder_fom DESC
                    """.trimMargin(),
                    mapOf<String, Any>("aktid" to id.id),
                ).map { row ->
                    OffNr(
                        OffNrId(row.long("id")),
                        gjelderFom = row.localDate("gjelder_fom"),
                        aktoerIdent = row.string("aktoer_ident"),
                    )
                }.asList,
            )
}
