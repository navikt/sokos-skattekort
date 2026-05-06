package no.nav.sokos.skattekort.skattekortdata

import java.time.Instant

import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekort.SkattekortId
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType

data class SkattekortData(
    val id: SkattekortDataId,
    val inntektsaar: Int,
    val dataMottatt: String,
    val fnr: Personidentifikator,
    val opprettet: Instant,
    val type: BestillingsbatchType? = null,
    val skattekortId: SkattekortId? = null,
)

@JvmInline
value class SkattekortDataId(
    val value: Long,
)
