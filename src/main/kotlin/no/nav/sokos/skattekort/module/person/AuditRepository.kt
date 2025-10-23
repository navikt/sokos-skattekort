package no.nav.sokos.skattekort.module.person

import kotliquery.TransactionalSession
import kotliquery.queryOf

object AuditRepository {
    fun insert(
        tx: TransactionalSession,
        tag: AuditTag,
        personId: PersonId,
        informasjon: String,
        brukerId: String? = null,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                INSERT INTO person_audit(person_id, tag, bruker_id, informasjon)
                VALUES (:person_id, :tag, :brukerId, :informasjon)
                """.trimIndent(),
                mapOf(
                    "person_id" to personId.value,
                    "tag" to tag.name,
                    "brukerId" to (brukerId ?: AUDIT_SYSTEM),
                    "informasjon" to informasjon,
                ),
            ),
        )

    fun getAuditByPersonId(
        tx: TransactionalSession,
        personId: PersonId,
    ): List<Audit> =
        tx.list(
            queryOf(
                """
                SELECT id, person_id, bruker_id, opprettet, tag, informasjon
                FROM person_audit
                WHERE person_id = :personId
                ORDER BY opprettet DESC
                """.trimIndent(),
                mapOf("personId" to personId.value),
            ),
            extractor = { row -> Audit(row) },
        )
}
