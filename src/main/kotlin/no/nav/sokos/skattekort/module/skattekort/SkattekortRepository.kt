package no.nav.sokos.skattekort.module.skattekort

import kotlinx.datetime.toJavaLocalDate

import kotliquery.Query
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk.Companion.ForskuddstrekkType.FRIKORT
import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk.Companion.ForskuddstrekkType.PROSENTKORT
import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk.Companion.ForskuddstrekkType.TABELLKORT

object SkattekortRepository {
    fun insertBatch(
        tx: TransactionalSession,
        skattekortList: List<Skattekort>,
    ): List<Long> =
        skattekortList.map { skattekort ->
            val id =
                tx.updateAndReturnGeneratedKey(
                    Query(
                        statement =
                            """
                            INSERT INTO skattekort (person_id, utstedt_dato, identifikator, inntektsaar, kilde, resultatForSkattekort) 
                            VALUES (:personId, :utstedtDato, :identifikator, :inntektsaar, :kilde, :resultatForSkattekort)
                            """.trimIndent(),
                        paramMap =
                            mapOf(
                                "personId" to skattekort.personId.value,
                                "utstedtDato" to skattekort.utstedtDato?.toJavaLocalDate(),
                                "identifikator" to skattekort.identifikator,
                                "inntektsaar" to skattekort.inntektsaar,
                                "kilde" to skattekort.kilde,
                                "resultatForSkattekort" to skattekort.resultatForSkattekort.value,
                            ),
                    ),
                )
            skattekortList.forEach { skattekort ->
                tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                    """
                    INSERT INTO forskuddstrekk (skattekort_id, trekk_kode, type, frikort_beloep, tabell_nummer, prosentsats, antall_mnd_for_trekk)
                    VALUES (:skattekortId, :trekk_kode, :type, :frikort_beloep, :tabell_nummer, :prosentsats, :antall_mnd_for_trekk)
                    """.trimIndent(),
                    skattekort.forskuddstrekkList.map { forskuddstrekk ->
                        when (forskuddstrekk) {
                            is Frikort ->
                                mapOf(
                                    "skattekortId" to id,
                                    "trekk_kode" to forskuddstrekk.trekkode,
                                    "type" to FRIKORT.type,
                                    "frikort_beloep" to forskuddstrekk.frikortBeloep,
                                    "tabell_nummer" to null,
                                    "prosentsats" to null,
                                    "antall_mnd_for_trekk" to null,
                                )

                            is Prosentkort ->
                                mapOf(
                                    "skattekortId" to id,
                                    "trekk_kode" to forskuddstrekk.trekkode,
                                    "type" to PROSENTKORT.type,
                                    "frikort_beloep" to null,
                                    "tabell_nummer" to null,
                                    "prosentsats" to forskuddstrekk.prosentSats,
                                    "antall_mnd_for_trekk" to null,
                                )

                            is Tabellkort ->
                                mapOf(
                                    "skattekortId" to id,
                                    "trekk_kode" to forskuddstrekk.trekkode,
                                    "type" to TABELLKORT.type,
                                    "frikort_beloep" to null,
                                    "tabell_nummer" to forskuddstrekk.tabellNummer,
                                    "prosentsats" to forskuddstrekk.prosentSats,
                                    "antall_mnd_for_trekk" to forskuddstrekk.antallMndForTrekk,
                                )

                            else -> mapOf()
                        }
                    },
                )
                tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                    """
                    INSERT INTO skattekort_tilleggsopplysning (skattekort_id, opplysning)
                    VALUES (:skattekortId, :opplysning)
                    """.trimIndent(),
                    skattekort.tilleggsopplysningList.map { tilleggsopplysning ->
                        mapOf(
                            "skattekortId" to id,
                            "opplysning" to tilleggsopplysning.opplysning,
                        )
                    },
                )
            }
            id ?: error("Failed to insert skattekort record")
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
