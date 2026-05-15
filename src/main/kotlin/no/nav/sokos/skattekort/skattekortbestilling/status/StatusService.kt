package no.nav.sokos.skattekort.skattekortbestilling.status

import javax.sql.DataSource

class StatusService(
    private val dataSource: DataSource,
) {
    fun statusForespoeresel(
        fnr: String,
        aar: Int,
        forsystem: String,
    ): Status = StatusRegelsett(dataSource).evaluate(fnr, aar, forsystem)
}
