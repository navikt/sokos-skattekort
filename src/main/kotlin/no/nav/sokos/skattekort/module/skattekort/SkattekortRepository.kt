package no.nav.sokos.skattekort.module.skattekort

import kotlinx.datetime.toJavaLocalDate

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.PersonId

object SkattekortRepository {
    fun insertBatch(
        tx: TransactionalSession,
        skattekortList: List<Skattekort>,
    ): List<Long> {
        val ids =
            tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                """
                INSERT INTO skattekort (person_id, utstedt_dato, identifikator, inntektsaar, kilde) 
                VALUES (:personId, :utstedtDato, :identifikator, :inntektsaar, :kilde)
                """.trimIndent(),
                skattekortList.map { skattekort ->
                    mapOf(
                        "personId" to skattekort.personId.value,
                        "utstedtDato" to skattekort.utstedtDato.toJavaLocalDate(),
                        "identifikator" to skattekort.identifikator,
                        "inntektsaar" to skattekort.inntektsaar,
                        "kilde" to skattekort.kilde,
                    )
                },
            )
        skattekortList.forEach { skattekort ->
            tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                """
                INSERT INTO skattekort_del (skattekort_id, trekk_kode, type, frikort_beloep, tabell_nummer, prosentsats, antall_mnd_for_trekk)
                VALUES (:skattekortId, :trekk_kode, :type, :frikort_beloep, :tabell_nummer, :prosentsats, :antall_mnd_for_trekk)
                """.trimIndent(),
                skattekort.forskuddstrekkList.map { forskuddstrekk ->
                    when (forskuddstrekk) {
                        is Frikort ->
                            mapOf(
                                "trekk_kode" to forskuddstrekk.trekkode,
                                "type" to "frikort",
                                "frikort_beloep" to forskuddstrekk.frikortBeloep,
                                "tabell_nummer" to null,
                                "prosentsats" to null,
                                "antall_mnd_for_trekk" to null,
                            )

                        is Prosentkort ->
                            mapOf(
                                "trekk_kode" to forskuddstrekk.trekkode,
                                "type" to "prosentkort",
                                "frikort_beloep" to null,
                                "tabell_nummer" to null,
                                "prosentsats" to forskuddstrekk.prosentSats,
                                "antall_mnd_for_trekk" to null,
                            )

                        is Tabellkort ->
                            mapOf(
                                "trekk_kode" to forskuddstrekk.trekkode,
                                "type" to "tabellkort",
                                "frikort_beloep" to null,
                                "tabell_nummer" to forskuddstrekk.tabellNummer,
                                "prosentsats" to forskuddstrekk.prosentSats,
                                "antall_mnd_for_trekk" to forskuddstrekk.antallMndForTrekk,
                            )

                        else -> mapOf()
                    }
                },
            )
        }
        return ids
    }

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
                Skattekort(row, findAllForskuddstrekkBySkattekortId(tx, id), findAllTilleggsopplysningBySkattekortId(tx, id))
            },
        )

    fun findAllForskuddstrekkBySkattekortId(
        tx: TransactionalSession,
        id: SkattekortId,
    ): List<Forskuddstrekk> =
        tx.list(
            queryOf(
                """
                SELECT * FROM forskuddstrekk 
                WHERE skattekort_id = :skattekkortId
                """.trimIndent(),
                mapOf(
                    "skattekkortId" to id.value,
                ),
            ),
            extractor = { row ->
                Forskuddstrekk.create(row)
            },
        )

    private fun findAllTilleggsopplysningBySkattekortId(
        tx: TransactionalSession,
        id: SkattekortId,
    ): List<Tilleggsopplysning> =
        tx.list(
            queryOf(
                """
                SELECT * FROM skattekort_tilleggsopplysning 
                WHERE skattekort_id = :skattekkortId
                """.trimIndent(),
                mapOf(
                    "skattekkortId" to id.value,
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
