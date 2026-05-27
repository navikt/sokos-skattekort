package no.nav.sokos.skattekort.person

import kotliquery.TransactionalSession
import kotliquery.queryOf

object AuditRepository {
    fun insert(
        tx: TransactionalSession,
        tag: AuditTag,
        personId: PersonId,
        informasjon: String,
        brukerId: String? = null,
    ): Long? {
        // language=SQL
        val sql =
            """
            INSERT INTO person_audit(person_id, tag, bruker_id, informasjon)
            VALUES (:person_id, :tag, :brukerId, :informasjon)
            """.trimIndent()
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                sql,
                mapOf(
                    "person_id" to personId.value,
                    "tag" to tag.name,
                    "brukerId" to (brukerId ?: AUDIT_SYSTEM),
                    "informasjon" to informasjon,
                ),
            ),
        )
    }

    fun insertBatch(
        tx: TransactionalSession,
        tag: AuditTag,
        personIds: List<PersonId>,
        informasjon: String,
        brukerId: String? = null,
    ) = run {
        // language=SQL
        val sql =
            """
            INSERT INTO person_audit(person_id, tag, bruker_id, informasjon)
            VALUES (:person_id, :tag, :brukerId, :informasjon)
            """.trimIndent()
        tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
            sql,
            personIds.map { personId ->
                mapOf(
                    "person_id" to personId.value,
                    "tag" to tag.name,
                    "brukerId" to (brukerId ?: AUDIT_SYSTEM),
                    "informasjon" to informasjon,
                )
            },
        )
    }

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
