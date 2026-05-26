package no.nav.sokos.skattekort.skattekortbestilling.status

import javax.sql.DataSource

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.forespoersel.Foedselsnummerkategori
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.person.Person
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchRepository
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.Utsending
import no.nav.sokos.skattekort.utsending.UtsendingRepository

interface Regel {
    fun applies(ctx: StatusContext): Boolean

    fun status(): Status
}

class StatusRegelsett(
    private val dataSource: DataSource,
) {
    val regler =
        listOf(
            UgyldigFnrForDetteMiljoeRegel(),
            KanIkkeBestilleFraSkatteetatenForKunstigFnrRegel(),
            IkkeForespurtRegel(),
            IkkeBestiltRegel(),
            BestiltRegel(),
            BestillingFeiletRegel(),
            SkattekortManglerRegel(),
            UgyldigForsystemRegel(),
            VenterPaaUtsendingRegel(),
            SendtForsystemRegel(),
        )

    fun evaluate(
        fnr: String,
        aar: Int,
        forsystem: String,
    ): Status {
        val ctx = StatusContext(fnr, aar, forsystem, dataSource)
        return regler.firstOrNull { it.applies(ctx) }?.status()
            ?: Status.UKJENT
    }
}

class UgyldigFnrForDetteMiljoeRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = !Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr).erGyldig(ctx.fnr)

    override fun status(): Status = Status.UGYLDIG_FNR
}

class KanIkkeBestilleFraSkatteetatenForKunstigFnrRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = !Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr).kanBestilleSkattekort(ctx.fnr)

    override fun status(): Status = Status.KUNSTIG_FNR
}

class IkkeForespurtRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = (ctx.person == null)

    override fun status(): Status = Status.IKKE_FORESPURT
}

class IkkeBestiltRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.bestilling != null && ctx.bestilling?.bestillingsbatchId == null

    override fun status(): Status = Status.IKKE_BESTILT
}

class BestiltRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.bestillingsbatch?.status == BestillingsbatchStatus.NY

    override fun status(): Status = Status.BESTILT
}

class BestillingFeiletRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.bestillingsbatch?.status == BestillingsbatchStatus.FEILET

    override fun status(): Status = Status.FEILET_I_BESTILLING
}

class SkattekortManglerRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.skattekort.isEmpty()

    override fun status(): Status = Status.IKKE_FORESPURT
}

class UgyldigForsystemRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean {
        try {
            Forsystem.fromValue(ctx.forsystem)
        } catch (_: NoSuchElementException) {
            return true
        }
        return false
    }

    override fun status(): Status = Status.UGYLDIG_FORSYSTEM
}

class VenterPaaUtsendingRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.utsending != null

    override fun status(): Status = Status.VENTER_PAA_UTSENDING
}

class SendtForsystemRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.utsending == null

    override fun status(): Status = Status.SENDT_FORSYSTEM
}

class StatusContext(
    val fnr: String,
    val aar: Int,
    val forsystem: String,
    private val dataSource: DataSource,
) {
    val person: Person? by lazy {
        dataSource.transaction { tx ->
            PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr))
        }
    }

    val bestilling: Bestilling? by lazy {
        val p = person ?: return@lazy null
        dataSource.transaction { tx ->
            BestillingRepository.findByPersonIdAndInntektsaar(tx, p.id!!, aar)
        }
    }

    val bestillingsbatch: Bestillingsbatch? by lazy {
        val batchId = bestilling?.bestillingsbatchId?.id ?: return@lazy null
        dataSource.transaction { tx ->
            BestillingsbatchRepository.findById(tx, batchId)
        }
    }

    val skattekort: List<Skattekort> by lazy {
        val p = person ?: return@lazy emptyList()
        dataSource.transaction { tx ->
            SkattekortRepository.findAllByPersonId(tx, listOf(p.id!!), listOf(aar), adminRole = false)
        }
    }

    val utsending: Utsending? by lazy {
        dataSource.transaction { tx ->
            UtsendingRepository.findByPersonIdAndInntektsaar(tx, Personidentifikator(fnr), aar, Forsystem.fromValue(forsystem))
        }
    }
}
