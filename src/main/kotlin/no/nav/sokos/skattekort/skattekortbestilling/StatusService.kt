package no.nav.sokos.skattekort.skattekortbestilling

import javax.sql.DataSource

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.person.Person
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.UtsendingRepository

class StatusService(
    private val dataSource: DataSource,
) {
    fun statusForespoeresel(
        fnr: String,
        aar: Int,
        forsystem: String,
    ): Status {
        val kategoriMapper: Foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)
        if (!kategoriMapper.kanBestilleSkattekort(fnr)) {
            return Status.UGYLDIG_FNR
        }
        val person: Person? =
            dataSource.transaction { tx ->
                PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr))
            }
        if (person == null) return Status.IKKE_FORESPURT

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
                    BestillingsbatchRepository.findById(tx, bestilling.bestillingsbatchId.id)
                }

            if (batch?.status == BestillingsbatchStatus.NY) {
                return Status.BESTILT
            } else if (batch?.status == BestillingsbatchStatus.FEILET) {
                return Status.FEILET_I_BESTILLING
            }
        }
        val skattekort =
            dataSource.transaction { tx ->
                SkattekortRepository.findAllByPersonId(tx, person.id!!, aar, adminRole = false)
            }

        if (skattekort.isNotEmpty()) {
            val validForsystem =
                try {
                    Forsystem.fromValue(forsystem)
                } catch (_: NoSuchElementException) {
                    return Status.UGYLDIG_FORSYSTEM
                }

            val utsending =
                dataSource.transaction { tx ->
                    UtsendingRepository.findByPersonIdAndInntektsaar(tx, Personidentifikator(fnr), aar, validForsystem)
                }
            return if (utsending != null) {
                Status.VENTER_PAA_UTSENDING
            } else {
                Status.SENDT_FORSYSTEM
            }
        }
        return Status.IKKE_FORESPURT
    }
}
