package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.Instant

import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.skattekort.toBestillSkattekortResponse
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchStatus
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchId

fun okBestillSkattekortResponse(ref: String): BestillSkattekortResponse =
    toBestillSkattekortResponse(
        """
        {
          "dialogreferanse": "any-dialog-ref",
          "bestillingsreferanse": "$ref"
        }
        """.trimIndent(),
    )

fun aBatch(
    id: Long,
    status: BestillingBatchStatus,
    type: String,
    bestillingsreferanse: String,
): BestillingBatch =
    BestillingBatch(
        id = BestillingsbatchId(id),
        status = status.value,
        type = type,
        bestillingsreferanse = bestillingsreferanse,
        oppdatert = Instant.DISTANT_PAST,
        opprettet = Instant.DISTANT_PAST,
        dataSendt = "",
    )

fun List<BestillingBatch>.shouldBeFunctionallyEquivalentTo(expected: List<BestillingBatch>) {
    this.size shouldBe expected.size
    expected.shouldContainAllIgnoringFields(
        expected,
        BestillingBatch::oppdatert,
        BestillingBatch::opprettet,
        BestillingBatch::id,
        BestillingBatch::dataSendt,
    )
}
