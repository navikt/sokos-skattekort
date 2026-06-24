package no.nav.sokos.skattekort.skattekortbestilling.status

import javax.sql.DataSource

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.forespoersel.Abonnement
import no.nav.sokos.skattekort.forespoersel.AbonnementRepository
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
            UgyldigFnrForDetteMiljoeRegel,
            UgyldigForsystemRegel,
            UkjentPersonRegel,
            BestiltOgVenterPaaBatchRegel,
            SendtBestillingRegel,
            BestillingFeiletRegel,
            VenterPaaUtsendingRegel,
            AbonnererRegel,
            AbonnererIkkeRegel,
        )

    fun evaluate(
        fnr: Personidentifikator,
        aar: Int,
        forsystem: String,
    ): Status {
        val ctx = StatusContext(fnr, aar, forsystem, dataSource)
        return regler.firstOrNull { it.applies(ctx) }?.status()
            ?: Status.UKJENT
    }
}

private object UgyldigFnrForDetteMiljoeRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = !Foedselsnummerkategori.valueOf(PropertiesConfig.applicationProperties.gyldigeFnr).erGyldig(ctx.fnr.value)

    override fun status(): Status = Status.UGYLDIG_FNR
}

private object UgyldigForsystemRegel : Regel {
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

private object UkjentPersonRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = (ctx.person == null)

    override fun status(): Status = Status.IKKE_FORESPURT
}

private object BestiltOgVenterPaaBatchRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.bestilling != null && ctx.bestilling?.bestillingsbatchId == null

    override fun status(): Status = Status.IKKE_BESTILT
}

private object SendtBestillingRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.bestillingsbatch?.status == BestillingsbatchStatus.NY

    override fun status(): Status = Status.BESTILT
}

private object BestillingFeiletRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.bestillingsbatch?.status == BestillingsbatchStatus.FEILET

    override fun status(): Status = Status.FEILET_I_BESTILLING
}

private object VenterPaaUtsendingRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.utsending != null

    override fun status(): Status = Status.VENTER_UTSENDING
}

private object AbonnererRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.abonnement.isNotEmpty()

    override fun status(): Status = Status.ABONNERER
}

private object AbonnererIkkeRegel : Regel {
    override fun applies(ctx: StatusContext): Boolean = ctx.abonnement.isEmpty()

    override fun status(): Status = Status.ABONNERER_IKKE
}

class StatusContext(
    val fnr: Personidentifikator,
    val aar: Int,
    val forsystem: String,
    private val dataSource: DataSource,
) {
    val person: Person? by lazy {
        dataSource.transaction { tx ->
            PersonRepository.findPersonByFnr(tx, fnr)
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
            UtsendingRepository.findByPersonIdAndInntektsaar(tx, fnr, aar, Forsystem.fromValue(forsystem))
        }
    }

    val abonnement: List<Abonnement> by lazy {
        dataSource.transaction { tx ->
            AbonnementRepository.abonnementsForFnrAndInntektsaar(tx, fnr, forsystem = Forsystem.fromValue(forsystem), inntektsaar = aar)
        }
    }
}
