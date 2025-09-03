package no.nav.sokos.skattekort.aktoer

import kotliquery.TransactionalSession
import kotliquery.queryOf

object AktoerRepository {
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
}
