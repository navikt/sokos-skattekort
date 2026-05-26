package no.nav.sokos.skattekort.skattekortbestilling

import java.time.Instant

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

object BestillingsbatchRepository {
    fun insert(
        tx: TransactionalSession,
        bestillingsreferanse: String,
        dataSendt: String,
        type: BestillingsbatchType,
    ): Long {
        @Language("PostgreSQL")
        val sql =
            """
                |INSERT INTO bestillingsbatcher (bestillingsreferanse, data_sendt, type)
                |VALUES (:bestillingsreferanse, (CAST (:dataSendt AS JSON)), :type)
            """.trimMargin()
        return tx.updateAndReturnGeneratedKey(
            queryOf(
                sql,
                mapOf(
                    "bestillingsreferanse" to bestillingsreferanse,
                    "dataSendt" to dataSendt,
                    "type" to type.name,
                ),
            ),
        ) ?: error("Failed to insert bestillingsbatch")
    }

    fun getAllUnprocessedBestillingsbatch(
        tx: TransactionalSession,
        type: BestillingsbatchType,
    ): List<Bestillingsbatch> {
        @Language("PostgreSQL")
        val sql =
            """
                |SELECT *
                |FROM bestillingsbatcher
                |WHERE status IN ('${BestillingsbatchStatus.NY}', '${BestillingsbatchStatus.RETRY}') AND type = :type
                |ORDER BY oppdatert ASC
            """.trimMargin()
        return tx.list(
            queryOf(
                sql,
                mapOf(
                    "type" to type.name,
                ),
            ),
            extractor = mapToBestillingsbatch,
        )
    }

    fun getFilteredBestillingsbatches(
        tx: TransactionalSession,
        instantStart: Instant?,
        instantEnd: Instant?,
    ): Map<Bestillingsbatch, String?> {
        var sqlparts: List<String> = ArrayList()
        sqlparts += """
                    |SELECT *
                    |FROM bestillingsbatcher
                    |WHERE 1 = 1
                    """
        if (instantStart != null) sqlparts += "AND (opprettet > :start OR oppdatert > :start)"
        if (instantEnd != null) sqlparts += "AND (opprettet < :end OR oppdatert < :end)"

        sqlparts += "ORDER BY oppdatert ASC"

        val statement = sqlparts.joinToString(" ").trimMargin()
        val query =
            queryOf(
                statement,
                mapOf(
                    "start" to instantStart,
                    "end" to instantEnd,
                ),
            )

        return tx
            .list(
                query,
                extractor = mapToBestillingsbatchWithDataMottatt,
            ).toMap()
    }

    fun getDefaultBatchInsightResults(tx: TransactionalSession): Map<Bestillingsbatch, String?> {
        @Language("PostgreSQL")
        val sql =
            """
                |SELECT id,
                | status,
                | type,
                | bestillingsreferanse,
                | data_sendt,
                | oppdatert,
                | opprettet,
                | data_mottatt
                |
                | FROM bestillingsbatcher
                |WHERE id IN (
                |        (SELECT id
                |            FROM bestillingsbatcher
                |            ORDER BY oppdatert DESC
                |            LIMIT 20)
                |   UNION
                |       (SELECT id
                |           FROM bestillingsbatcher
                |           WHERE status <> 'FERDIG')
                |           )
                |ORDER BY oppdatert DESC
            """.trimMargin()
        val query = queryOf(sql)
        return tx
            .list(
                query,
                extractor = mapToBestillingsbatchWithDataMottatt,
            ).toMap()
    }

    fun findById(
        tx: TransactionalSession,
        bestillingsbatchId: Long,
    ): Bestillingsbatch? {
        @Language("PostgreSQL")
        val sql =
            """
                |SELECT id,
                | status,
                | type,
                | bestillingsreferanse,
                | data_sendt,
                | oppdatert,
                | opprettet
                |
                |FROM bestillingsbatcher
                |WHERE id = :id
            """.trimMargin()
        return tx.single(
            queryOf(
                sql,
                mapOf("id" to bestillingsbatchId),
            ),
            extractor = mapToBestillingsbatch,
        )
    }

    fun markAs(
        tx: TransactionalSession,
        bestillingsbatchId: Long,
        status: BestillingsbatchStatus,
    ) {
        @Language("PostgreSQL")
        val sql =
            """
                |UPDATE bestillingsbatcher
                |SET status = :status, oppdatert = NOW()
                |WHERE id = :id
            """.trimMargin()
        tx.run(
            queryOf(
                sql,
                mapOf(
                    "id" to bestillingsbatchId,
                    "status" to status.name,
                ),
            ).asExecute,
        )
    }

    fun updateBestillingsbatchWithMottatteData(
        tx: TransactionalSession,
        batchId: Long,
        dataMottatt: String,
    ) {
        @Language("PostgreSQL")
        val sql =
            """
                |UPDATE bestillingsbatcher
                |SET data_mottatt = :dataMottatt, oppdatert = NOW()
                |WHERE id = :id
            """.trimMargin()
        tx.run(
            queryOf(
                sql,
                mapOf(
                    "id" to batchId,
                    "dataMottatt" to dataMottatt,
                ),
            ).asExecute,
        )
    }

    fun getFirstNotFerdigBestillingsbatch(tx: TransactionalSession): Bestillingsbatch? {
        @Language("PostgreSQL")
        val sql =
            """
            |SELECT id,
            | status,
            | type,
            | bestillingsreferanse,
            | data_sendt,
            | oppdatert,
            | opprettet
            |
            |FROM bestillingsbatcher WHERE status IN ('${BestillingsbatchStatus.NY}', '${BestillingsbatchStatus.RETRY}') ORDER BY opprettet LIMIT 1
            """.trimMargin()
        return tx.single(
            queryOf(sql),
            extractor = mapToBestillingsbatch,
        )
    }

    fun rerun(
        tx: TransactionalSession,
        id: Long,
    ): Int {
        @Language("PostgreSQL")
        val sql =
            """
                |UPDATE bestillingsbatcher
                |SET status = :retry, oppdatert = NOW()
                |WHERE id = :id
                |AND status = :feilet
            """.trimMargin()
        return tx.run(
            queryOf(
                sql,
                mapOf(
                    "id" to id,
                    "retry" to BestillingsbatchStatus.RETRY.name,
                    "feilet" to BestillingsbatchStatus.FEILET.name,
                ),
            ).asUpdate,
        )
    }

    fun getIncompleteBatches(tx: TransactionalSession): List<Bestillingsbatch> {
        @Language("PostgreSQL")
        val sql =
            """
                |SELECT id,
                | status,
                | type,
                | bestillingsreferanse,
                | data_sendt,
                | oppdatert,
                | opprettet
                |
                |FROM bestillingsbatcher
                |WHERE status <> :ferdig
                |ORDER BY oppdatert DESC
            """.trimMargin()
        return tx.list(
            queryOf(
                sql,
                mapOf(
                    "ferdig" to BestillingsbatchStatus.FERDIG.name,
                ),
            ),
            extractor = mapToBestillingsbatch,
        )
    }

    val mapToBestillingsbatch: (Row) -> Bestillingsbatch = { row ->
        Bestillingsbatch(
            id = BestillingsbatchId(row.long("id")),
            status = BestillingsbatchStatus.valueOf(row.string("status")),
            type = BestillingsbatchType.valueOf(row.string("type")),
            bestillingsreferanse = row.string("bestillingsreferanse"),
            dataSendt = row.string("data_sendt"),
            oppdatert = row.instant("oppdatert"),
            opprettet = row.instant("opprettet"),
        )
    }

    val mapToBestillingsbatchWithDataMottatt: (Row) -> Pair<Bestillingsbatch, String?> = { row ->
        mapToBestillingsbatch(row) to row.stringOrNull("data_mottatt")
    }
}
