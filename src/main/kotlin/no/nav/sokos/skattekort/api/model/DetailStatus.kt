package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

@Serializable
data class DetailStatus(
    val harForespoersel: Boolean,
    val abonnements: List<String>,
    val skattekortLastYear: Boolean,
    val skattekortThisYear: Boolean,
    val skattekortNextYear: Boolean,
)
