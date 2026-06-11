package no.nav.sokos.skattekort.person

import kotliquery.TransactionalSession
import kotliquery.queryOf

object AuditRepository {
    fun insertBatch(
        tx: TransactionalSession,
        tag: AuditTag,
        personIdList: List<PersonId>,
        informasjon: String,
        brukerId: String? = null,
    ) = run {
        // language=SQL
        val sql =
            """
            INSERT INTO person_audit(person_id, tag, bruker_id, informasjon)
            VALUES (:personId, :tag, :brukerId, :informasjon)
            """.trimIndent()
        tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
            sql,
            personIdList.map { personId ->
                mapOf(
                    "personId" to personId.value,
                    "tag" to tag.name,
                    "brukerId" to (brukerId ?: AUDIT_SYSTEM),
                    "informasjon" to informasjon,
                )
            },
        )
    }

    fun insert(
        tx: TransactionalSession,
        tag: AuditTag,
        personId: PersonId,
        informasjon: String,
        brukerId: String? = null,
    ): Long? = insertBatch(tx, tag, listOf(personId), informasjon, brukerId).firstOrNull()

    fun getAuditByPersonId(
        tx: TransactionalSession,
        personId: PersonId,
    ): List<Audit> {
        // language=SQL
        val sql =
            """
            SELECT id, person_id, bruker_id, opprettet, tag, informasjon
            FROM person_audit
            WHERE person_id = :personId
            ORDER BY id
            """.trimIndent()
        return tx.list(
            queryOf(
                sql,
                mapOf("personId" to personId.value),
            ),
            extractor = { row -> Audit(row) },
        )
    }
}
