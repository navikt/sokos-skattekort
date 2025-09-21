package no.nav.sokos.skattekort.domain.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.domain.person.PersonId
import no.nav.sokos.skattekort.domain.person.Personidentifikator

object BestillingRepository {
    fun getAllBestilling(tx: TransactionalSession): List<Bestilling> =
        tx.list(
            queryOf(
                """
                SELECT * FROM bestillinger
                """.trimIndent(),
            ),
            extractor = mapToBestilling,
        )

    fun insert(
        tx: TransactionalSession,
        bestilling: Bestilling,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                    |INSERT INTO bestillinger (person_id, aar, fnr) 
                    |VALUES (:personId, :aar, :fnr)
                """.trimMargin(),
                mapOf(
                    "personId" to bestilling.personId.value,
                    "aar" to bestilling.aar,
                    "fnr" to bestilling.fnr.value,
                ),
            ),
        )

    @OptIn(ExperimentalTime::class)
    private val mapToBestilling: (Row) -> Bestilling = { row ->
        Bestilling(
            id = BestillingId(row.long("id")),
            personId = PersonId(row.long("person_id")),
            fnr = Personidentifikator(row.string("fnr")),
            aar = row.int("aar"),
            bestillingBatchId = row.longOrNull("bestilling_batch_id")?.let { BestillingBatchId(it) },
            oppdatert = row.instant("oppdatert").toKotlinInstant(),
        )
    }
}
