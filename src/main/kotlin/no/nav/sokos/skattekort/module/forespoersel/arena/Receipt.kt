package no.nav.sokos.skattekort.module.forespoersel.arena

data class Receipt(
    val receiptId: Int,
    val archiveReference: String? = null,
    val receiversReference: String? = null,
)
