package no.nav.sokos.skattekort.skattekortbestilling.status

import javax.sql.DataSource

import no.nav.sokos.skattekort.api.model.DetailStatus
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class StatusService(
    private val dataSource: DataSource,
    private val tilgangsmaskinClientService: TilgangsmaskinClientService,
) {
    suspend fun statusForespoeresel(
        fnr: String,
        aar: Int,
        forsystem: String,
        saksbehandler: Saksbehandler,
    ): Status {
        if (tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, fnr) != null) {
            return Status.SKJERMET
        }

        return StatusRegelsett(dataSource).evaluate(fnr, aar, forsystem)
    }

    fun statusForespoersler(fnr: Collection<Personidentifikator>): Map<String, DetailStatus> =
        dataSource.transaction { tx ->
            SkattekortRepository.getDetailStatus(tx, fnr)
        }
}
