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
                ORDER BY opprettet DESC
                """.trimIndent(),
                mapOf(
                    "personId" to personId.value,
                    "inntektsaar" to inntektsaar,
                ),
            ),
            extractor = { row ->
                val id = SkattekortId(row.long("id"))
                Skattekort(row, skattekortDeler(tx, id), tilleggsopplysninger(tx, id))
            },
        )

    fun skattekortDeler(
        tx: TransactionalSession,
        id: SkattekortId,
    ): List<SkattekortDel> =
        tx.list(
            queryOf(
                """
                SELECT * FROM skattekort_del 
                WHERE skattekort_id = :skId
                """.trimIndent(),
                mapOf(
                    "skId" to id.value,
                ),
            ),
            extractor = { row ->
                SkattekortDel.create(row)
            },
        )

    private fun tilleggsopplysninger(
        tx: TransactionalSession,
        id: SkattekortId,
    ): List<Tilleggsopplysning> =
        tx.list(
            queryOf(
                """
                SELECT * FROM skattekort_tilleggsopplysning 
                WHERE skattekort_id = :skId
                """.trimIndent(),
                mapOf(
                    "skId" to id.value,
                ),
            ),
            extractor = { row ->
                Tilleggsopplysning(row)
            },
        )

    fun findLatestByPersonId(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
    ): Skattekort = findAllByPersonId(tx, personId, inntektsaar).first()
}
