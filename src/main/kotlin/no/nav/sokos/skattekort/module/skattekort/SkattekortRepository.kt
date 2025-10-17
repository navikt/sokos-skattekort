package no.nav.sokos.skattekort.module.skattekort

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.util.SQLUtils.asMap

object SkattekortRepository {
    fun insertBatch(
        tx: TransactionalSession,
        skattekortList: List<Skattekort>,
    ): List<Long> =
        tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
            """
            INSERT INTO skattekort (person_id, utstedt_dato, identifikator, inntektsaar, kilde) 
            VALUES (:personId, :utstedtDato, :identifikator, :inntektsaar, :kilde)
            """.trimIndent(),
            skattekortList.map { skattekort ->
                skattekort.asMap()
            },
        )

    fun findAllByPersonId(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
    ): List<Skattekort> =
        tx.list(
            queryOf(
                """
                SELECT * FROM skattekort 
                WHERE person_id = :personId AND inntektsaar = :inntektsaar
                """.trimIndent(),
                mapOf(
                    "personId" to personId.value,
                    "inntektsaar" to inntektsaar,
                ),
            ),
            extractor = { row -> Skattekort(row) },
        )
}
