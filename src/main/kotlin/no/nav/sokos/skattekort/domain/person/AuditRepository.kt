package no.nav.sokos.skattekort.domain.person

import kotliquery.TransactionalSession
import kotliquery.queryOf

object AuditRepository {
    fun insert(
        tx: TransactionalSession,
        tag: AuditTag,
        personId: PersonId,
        informasjon: String,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """INSERT INTO person_audit
            |(person_id, tag, bruker_id, informasjon)
            | VALUES (:person_id, :tag, :bruker, :informasjon)
                """.trimMargin(),
                mapOf(
                    "person_id" to personId.value,
                    "tag" to tag.name,
                    "bruker" to "system",
                    "informasjon" to informasjon,
                ),
            ),
        )
}
