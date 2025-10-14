package no.nav.sokos.skattekort.domain.arena

data class Receipt(
    val receiptId: Int,
    val archiveReference: String? = null,
    val receiversReference: String? = null,
)
