package no.nav.sokos.skattekort.module.status

import javax.sql.DataSource

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.module.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.Bestilling
import no.nav.sokos.skattekort.module.skattekort.BestillingBatchRepository
import no.nav.sokos.skattekort.module.skattekort.BestillingBatchStatus
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.skattekort.Status
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class StatusService(
    private val dataSource: DataSource,
) {
    fun statusForespoeresel(
        fnr: String,
        aar: Int,
        forsystem: String,
    ): Status {
        val kategoriMapper: Foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)
        if (!kategoriMapper.erGyldig(fnr)) {
            return Status.UGYLDIG_FNR
        }
        val person: Person? =
            dataSource.transaction { tx ->
                PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr))
            }
        if (person == null) return Status.IKKE_FNR

        val bestilling: Bestilling? =
            dataSource.transaction { tx ->
                BestillingRepository.findByPersonIdAndInntektsaar(tx, person.id!!, aar)
            }
        if (bestilling != null) {
            if (bestilling.bestillingsbatchId == null) {
                return Status.IKKE_BESTILT
            }

            val batch =
                dataSource.transaction { tx ->
                    BestillingBatchRepository.findById(tx, bestilling.bestillingsbatchId.id)
                }

            if (batch?.status == BestillingBatchStatus.Ny.value) {
                return Status.BESTILT
            } else if (batch?.status == BestillingBatchStatus.Feilet.value) {
                return Status.FEILET_I_BESTILLING
            }
        }
        val skattekort =
            dataSource.transaction { tx ->
                SkattekortRepository.findAllByPersonId(tx, person.id!!, aar, adminRole = false)
            }

        if (skattekort.isNotEmpty()) {
            val utsending =
                dataSource.transaction { tx ->
                    UtsendingRepository.findByPersonIdAndInntektsaar(tx, Personidentifikator(fnr), aar, Forsystem.fromValue(forsystem))
                }
            return if (utsending != null) {
                Status.VENTER_PAA_UTSENDING
            } else {
                Status.SENDT_FORSYSTEM
            }
        }
        return Status.UKJENT
    }
}
