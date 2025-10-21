package no.nav.sokos.skattekort.module.skattekort

import kotlinx.serialization.Serializable

data class SkattekortDel(
    val id: SkattekortDelId? = null,
    val skattekortId: SkattekortId,
    val trekkKode: String,
    val skattekortType: SkattekortType,
    val frikortBeloep: Int? = null,
    val tabellNummer: String? = null,
    val prosentsats: Double? = null,
    val antallMndForTrekk: Double? = null,
)

@Serializable
@JvmInline
value class SkattekortDelId(
    val value: Long,
)
