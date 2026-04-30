package no.nav.sokos.skattekort.forespoersel

import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

object ForespoerselRepository {
    fun insert(
        tx: TransactionalSession,
        forsystem: Forsystem,
        dataMottatt: String,
    ): Long =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                INSERT INTO forespoersler (forsystem, data_mottatt)
                VALUES (:forsystem, :data_mottatt)
                """.trimIndent(),
                mapOf(
                    "forsystem" to forsystem.value,
                    "data_mottatt" to dataMottatt,
                ),
            ),
        ) ?: throw IllegalStateException("Failed to insert forespoersel")

    fun getAllForespoersel(
        tx: TransactionalSession,
        count: Int = 1000,
        offset: Int = 0,
    ): List<Forespoersel> =
        tx.list(
            queryOf(
                """
                SELECT * FROM forespoersler
                ORDER BY id ASC
                LIMIT :count OFFSET :offset
                """.trimIndent(),
                mapOf("count" to count, "offset" to offset),
            ),
            mapToForespoersel,
        )

    fun getAllForespoerselInput(tx: TransactionalSession): List<ForespoerselInput> =
        tx.list(
            queryOf(
                """
                SELECT * FROM forespoersel_input
                """.trimIndent(),
            ),
            mapToForespoerselInput,
        )

    fun deleteAllForespoerselInput(tx: TransactionalSession) {
        tx.update(
            queryOf(
                """DELETE FROM forespoersel_input""",
            ),
        )
    }

    private val mapToForespoerselInput: (Row) -> ForespoerselInput = { row ->
        ForespoerselInput(
            forsystem = Forsystem.fromValue(row.string("forsystem")),
            inntektsaar = row.int("inntektsaar"),
            fnrList = listOf(row.string("fnr")),
        )
    }

    private val mapToForespoersel: (Row) -> Forespoersel = { row ->
        Forespoersel(
            id = ForespoerselId(row.long("id")),
            forsystem = Forsystem.fromValue(row.string("forsystem")),
            dataMottatt = row.string("data_mottatt"),
            opprettet = row.instant("opprettet").toKotlinInstant(),
        )
    }
}
