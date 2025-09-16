package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDateTime

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.person.Person
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonRepository

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
    ): List<Triple<Forsystem, String, LocalDateTime>> =
        tx.run(
            queryOf(
                """
                SELECT * FROM forespoersel
                ORDER BY id ASC
                LIMIT :count OFFSET :offset
                """.trimIndent(),
                mapOf("count" to count, "offset" to offset),
            ).map { row ->
                Triple(
                    Forsystem.fromValue(row.string("forsystem")),
                    row.string("data_mottatt"),
                    row.localDateTime("opprettet"),
                )
            }.asList,
        )

    fun createSkattekortforespoersler(
        it: TransactionalSession,
        forespoerselId: Long,
        aar: Int,
        persons: List<Person>,
    ) {
        persons.forEach { person ->
            it.run(
                queryOf(
                    """
                    INSERT INTO forespoersel_skattekort (forespoersel_id, person_id, aar)
                    VALUES (:forespoersel_id, :person_id, :aar)
                    """.trimIndent(),
                    mapOf(
                        "forespoersel_id" to forespoerselId,
                        "person_id" to person.id.id,
                        "aar" to aar,
                    ),
                ).asUpdate,
            )
        }
    }

    private fun findForespoerselById(
        it: TransactionalSession,
        id: Long,
    ): Forespoersel =
        it.run(
            queryOf(
                """
                SELECT * FROM forespoersel WHERE id = :id
                """.trimIndent(),
                mapOf("id" to id),
            ).map { row ->
                Forespoersel(
                    forsystem = Forsystem.fromValue(row.string("forsystem")),
                    inntektYear = row.string("data_mottatt").split(";")[1].toInt(),
                    persons = emptyList(), // Persons are not loaded here
                )
            }.asSingle,
        ) ?: throw IllegalStateException("Forespørsel with id $id not found")

    fun listSkattekortForespoersler(it: TransactionalSession): List<Skattekortforespoersel> =
        it.run(
            queryOf(
                """
                SELECT forespoersel_id, aar, person_id FROM forespoersel_skattekort
                """.trimIndent(),
            ).map { row ->
                Skattekortforespoersel(
                    forespoersel = findForespoerselById(it, row.long("forespoersel_id")),
                    aar = row.int("aar"),
                    person = PersonRepository().findPersonById(it, PersonId(row.long("person_id"))),
                )
            }.asList,
        )
}
