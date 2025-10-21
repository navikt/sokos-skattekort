package no.nav.sokos.skattekort.module.skattekort

import kotlinx.serialization.Serializable

data class SkattekortTileggsopplysning(
    val id: SkattekortTileggsopplysningId? = null,
    val skattekortId: SkattekortId,
    val opplysning: String,
)

@Serializable
@JvmInline
value class SkattekortTileggsopplysningId(
    val value: Long,
)
