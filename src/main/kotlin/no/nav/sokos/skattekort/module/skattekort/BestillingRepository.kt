package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator

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
                    |INSERT INTO bestillinger (person_id, inntektsaar, fnr) 
                    |VALUES (:personId, :inntektsaar, :fnr)
                """.trimMargin(),
                mapOf(
                    "personId" to bestilling.personId.value,
                    "inntektsaar" to bestilling.inntektsaar,
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
            inntektsaar = row.int("inntektsaar"),
            bestillingsbatchId = row.longOrNull("bestillingsbatch_id")?.let { BestillingsbatchId(it) },
            oppdatert = row.instant("oppdatert").toKotlinInstant(),
        )
    }
}
