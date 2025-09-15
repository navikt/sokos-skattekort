package no.nav.sokos.skattekort.forespoersel

import kotliquery.TransactionalSession
import kotliquery.queryOf

class ForespoerselRepository {
    fun create(
        tx: TransactionalSession,
        forsystem: Forsystem,
        data_mottatt: String,
    ): Long {
        val key =
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO forespoersel (forsystem, data_mottatt)
                    VALUES (:forsystem, :data_mottatt)
                    """.trimIndent(),
                    mapOf("forsystem" to forsystem.kode, "data_mottatt" to data_mottatt),
                ),
            ) ?: throw IllegalStateException("Failed to insert forespoersel")
        return key
    }

    fun list(
        tx: TransactionalSession,
        count: Int = 1000,
        offset: Int = 0,
    ): List<Forespoersel> =
        tx.run(
            queryOf(
                """
                SELECT * FROM forespoersel
                ORDER BY id ASC
                LIMIT :count OFFSET :offset
                """.trimIndent(),
                mapOf("count" to count, "offset" to offset),
            ).map { row ->
                Forespoersel(
                    forsystem = Forsystem.fromValue(row.string("forsystem")),
                    data_mottatt = row.string("data_mottatt"),
                    opprettet = row.localDateTime("opprettet"),
                )
            }.asList,
        )
}
