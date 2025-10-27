package no.nav.sokos.skattekort.module.person

import javax.sql.DataSource

import no.nav.sokos.skattekort.util.SQLUtils.transaction

class AuditService(
    private val dataSource: DataSource,
) {
    fun getAuditByPersonId(personId: PersonId): List<Audit> =
        dataSource.transaction { tx ->
            AuditRepository.getAuditByPersonId(tx, personId)
        }
}
