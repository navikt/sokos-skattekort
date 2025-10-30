package no.nav.sokos.skattekort.module.skattekort

import kotlinx.datetime.toJavaLocalDate

import kotliquery.Query
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator
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
                            INSERT INTO skattekort (person_id, utstedt_dato, identifikator, inntektsaar, kilde) 
                            VALUES (:personId, :utstedtDato, :identifikator, :inntektsaar, :kilde)
                            """.trimIndent(),
                        paramMap =
                            mapOf(
                                "personId" to skattekort.personId.value,
                                "utstedtDato" to skattekort.utstedtDato.toJavaLocalDate(),
                                "identifikator" to skattekort.identifikator,
                                "inntektsaar" to skattekort.inntektsaar,
                                "kilde" to skattekort.kilde,
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

    fun findLatestByPersonIdentifikator(
        tx: TransactionalSession,
        fnr: Personidentifikator,
        inntektsaar: Int,
    ): Skattekort? =
        tx.single(
            queryOf(
                """
                SELECT s.* 
                FROM skattekort s
                         INNER JOIN foedselsnumre f ON s.person_id = f.person_id
                WHERE f.fnr = :fnr AND s.inntektsaar = :inntektsaar
                ORDER BY s.opprettet DESC
                LIMIT 1
                """.trimIndent(),
                mapOf(
                    "fnr" to fnr.value,
                    "inntektsaar" to inntektsaar,
                ),
            ),
            extractor = { row ->
                val id = SkattekortId(row.long("id"))
                Skattekort(row, findAllForskuddstrekkBySkattekortId(tx, id), findAllTilleggsopplysningBySkattekortId(tx, id))
            },
        )
}
