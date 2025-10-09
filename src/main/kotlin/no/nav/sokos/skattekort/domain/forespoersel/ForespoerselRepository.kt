package no.nav.sokos.skattekort.domain.forespoersel

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

object ForespoerselRepository {
    fun insert(
        tx: TransactionalSession,
        forsystem: Forsystem,
        dataMottatt: String,
    ): Long {
        val key =
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO forespoersler (forsystem, data_mottatt)
                    VALUES (:forsystem, :data_mottatt)
                    """.trimIndent(),
                    mapOf(
                        "forsystem" to forsystem.kode,
                        "data_mottatt" to dataMottatt,
                    ),
                ),
            ) ?: throw IllegalStateException("Failed to insert forespoersel")
        return key
    }

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

    fun findById(
        tx: TransactionalSession,
        id: Long,
    ): Forespoersel? =
        tx.single(
            queryOf(
                """
                SELECT * FROM forespoersler WHERE id = :id
                """.trimIndent(),
                mapOf("id" to id),
            ),
            mapToForespoersel,
        )

    @OptIn(ExperimentalTime::class)
    private val mapToForespoersel: (Row) -> Forespoersel = { row ->
        Forespoersel(
            id = ForespoerselId(row.long("id")),
            forsystem = Forsystem.fromValue(row.string("forsystem")),
            dataMottatt = row.string("data_mottatt"),
            opprettet = row.instant("opprettet").toKotlinInstant(),
        )
    }
}
