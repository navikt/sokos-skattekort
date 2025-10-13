package no.nav.sokos.skattekort.domain.utsending

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

import kotliquery.Row
import kotliquery.Session
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.domain.forespoersel.AbonnementId
import no.nav.sokos.skattekort.domain.forespoersel.Forsystem
import no.nav.sokos.skattekort.domain.person.Personidentifikator

object UtsendingRepository {
    fun insertBatch(
        tx: TransactionalSession,
        utsendingList: List<Utsending>,
    ): List<Int> =
        tx
            .batchPreparedStatement(
                """
                INSERT INTO utsendinger (abonnement_id, fnr, inntektsaar, forsystem)
                VALUES (:abonnementId, :fnr, :inntektsaar, :forsystem)
                """.trimIndent(),
                utsendingList.map {
                    listOf(
                        "abonnementId" to it.abonnementId.value,
                        "fnr" to it.fnr.value,
                        "innteksaar" to it.inntektsaar,
                        "forsystem" to it.forsystem.kode,
                    )
                },
            ).orEmpty()

    fun delete(
        tx: TransactionalSession,
        id: UtsendingId,
    ) {
        tx.execute(
            queryOf(
                """
                DELETE FROM utsendinger WHERE id = :id
                """.trimIndent(),
                listOf("id" to id.value),
            ),
        )
    }

    fun getAllUtsendinger(session: Session): List<Utsending> =
        session.list(
            queryOf(
                """
                SELECT id, abonnement_id, fnr, inntektsaar, forsystem, opprettet
                FROM utsendinger
                """.trimIndent(),
            ),
            extractor = mapToUtsending,
        )

    @OptIn(ExperimentalTime::class)
    private val mapToUtsending: (Row) -> Utsending = { row ->
        Utsending(
            id = row.long("id")?.let { UtsendingId(it) },
            abonnementId = AbonnementId(row.long("abonnement_id")),
            fnr = Personidentifikator(row.string("fnr")),
            inntektsaar = row.int("inntektsaar"),
            forsystem = Forsystem.fromValue(row.string("forsystem")),
            opprettet = row.instant("opprettet").toKotlinInstant(),
        )
    }
}
