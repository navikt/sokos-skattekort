package no.nav.sokos.skattekort.forespoersel

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

object ForespoerselRepository {
    fun insert(
        tx: TransactionalSession,
        forsystem: Forsystem,
        dataMottatt: String,
    ): Long {
        @Language("PostgreSQL")
        val sql =
            """
            INSERT INTO forespoersler (forsystem, data_mottatt)
            VALUES (:forsystem, :data_mottatt)
            """.trimIndent()
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                sql,
                mapOf(
                    "forsystem" to forsystem.value,
                    "data_mottatt" to dataMottatt,
                ),
            ),
        ) ?: throw IllegalStateException("Failed to insert forespoersel")
    }

    fun getAllForespoersel(
        tx: TransactionalSession,
        count: Int = 1000,
        offset: Int = 0,
    ): List<Forespoersel> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT * FROM forespoersler
            ORDER BY id ASC
            LIMIT :count OFFSET :offset
            """.trimIndent()
        return tx.list(
            queryOf(
                sql,
                mapOf("count" to count, "offset" to offset),
            ),
            mapToForespoersel,
        )
    }

    fun getAllForespoerselInput(tx: TransactionalSession): List<ForespoerselInput> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT * FROM forespoersel_input
            """.trimIndent()
        return tx.list(
            queryOf(sql),
            mapToForespoerselInput,
        )
    }

    fun deleteAllForespoerselInput(tx: TransactionalSession) {
        @Language("PostgreSQL")
        val sql =
            "DELETE FROM forespoersel_input"
        tx.update(
            queryOf(sql),
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
            opprettet = row.instant("opprettet"),
        )
    }
}
