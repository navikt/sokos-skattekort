package no.nav.sokos.skattekort.module.skattekort

import kotlinx.datetime.toJavaLocalDate

import kotliquery.Query
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
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
            AuditRepository.insert(
                tx,
                AuditTag.SKATTEKORTINFORMASJON_MOTTATT,
                skattekort.personId,
                "Lagret skattekortresultat ${skattekort.resultatForSkattekort} for ${skattekort.inntektsaar}",
            )
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
            if (skattekort.forskuddstrekkList.isNotEmpty()) {
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
                                    "trekk_kode" to forskuddstrekk.trekkode.value,
                                    "type" to FRIKORT.type,
                                    "frikort_beloep" to forskuddstrekk.frikortBeloep,
                                    "tabell_nummer" to null,
                                    "prosentsats" to null,
                                    "antall_mnd_for_trekk" to null,
                                )

                            is Prosentkort ->
                                mapOf(
                                    "skattekortId" to id,
                                    "trekk_kode" to forskuddstrekk.trekkode.value,
                                    "type" to PROSENTKORT.type,
                                    "frikort_beloep" to null,
                                    "tabell_nummer" to null,
                                    "prosentsats" to forskuddstrekk.prosentSats,
                                    "antall_mnd_for_trekk" to null,
                                )

                            is Tabellkort ->
                                mapOf(
                                    "skattekortId" to id,
                                    "trekk_kode" to forskuddstrekk.trekkode.value,
                                    "type" to TABELLKORT.type,
                                    "frikort_beloep" to null,
                                    "tabell_nummer" to forskuddstrekk.tabellNummer,
                                    "prosentsats" to forskuddstrekk.prosentSats,
                                    "antall_mnd_for_trekk" to forskuddstrekk.antallMndForTrekk,
                                )
                        }
                    },
                )
            }
            if (skattekort.tilleggsopplysningList.isNotEmpty()) {
                tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                    """
                    INSERT INTO skattekort_tilleggsopplysning (skattekort_id, opplysning)
                    VALUES (:skattekortId, :opplysning)
                    """.trimIndent(),
                    skattekort.tilleggsopplysningList.map { tilleggsopplysning ->
                        mapOf(
                            "skattekortId" to id,
                            "opplysning" to tilleggsopplysning.value,
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
        adminRole: Boolean,
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
                Skattekort(row, findAllForskuddstrekkBySkattekortId(tx, id, adminRole = adminRole), findAllTilleggsopplysningBySkattekortId(tx, id, adminRole))
            },
        )

    fun findAllForskuddstrekkBySkattekortId(
        tx: TransactionalSession,
        id: SkattekortId,
        adminRole: Boolean,
    ): List<Forskuddstrekk> =
        tx
            .list(
                queryOf(
                    """
                    SELECT * FROM forskuddstrekk 
                    WHERE skattekort_id = :skattekortId
                    """.trimIndent(),
                    mapOf(
                        "skattekortId" to id.value,
                    ),
                ),
                extractor = { row ->
                    val ft = Forskuddstrekk.create(row)
                    if (ft.requiresAdminRole() && !adminRole) {
                        null
                    } else {
                        ft
                    }
                },
            ).filterNotNull()

    private fun findAllTilleggsopplysningBySkattekortId(
        tx: TransactionalSession,
        id: SkattekortId,
        adminRole: Boolean,
    ): List<Tilleggsopplysning> =
        tx
            .list(
                queryOf(
                    """
                    SELECT * FROM skattekort_tilleggsopplysning 
                    WHERE skattekort_id = :skattekortId
                    """.trimIndent(),
                    mapOf(
                        "skattekortId" to id.value,
                    ),
                ),
                extractor = { row ->
                    val to = Tilleggsopplysning.fromValue(row.string("opplysning"))
                    if (to.requiresAdminRole && !adminRole) {
                        null
                    } else {
                        to
                    }
                },
            ).filterNotNull()

    fun findLatestByPersonId(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
        adminRole: Boolean,
    ): Skattekort = findAllByPersonId(tx, personId, inntektsaar, adminRole).first()

    fun getSecondsSinceLatestSkattekortOpprettet(tx: TransactionalSession): Double? =
        tx.single(
            queryOf(
                """
                SELECT EXTRACT(EPOCH FROM NOW() - MAX(opprettet)) AS sekunder_siden_siste_skattekort
                    FROM skattekort
                """.trimIndent(),
            ),
            extractor = { row -> row.doubleOrNull("sekunder_siden_siste_skattekort") },
        )

    fun numberOfSkattekortByResultatForSkattekortMetrics(tx: TransactionalSession): Map<ResultatForSkattekort, Int> =
        tx
            .list(
                queryOf(
                    """
                    SELECT resultatForSkattekort, COUNT(1) AS antall 
                    FROM skattekort
                    GROUP BY resultatForSkattekort
                    """.trimIndent(),
                ),
                extractor = { row ->
                    val resultat = ResultatForSkattekort.fromValue(row.string("resultatForSkattekort"))
                    val count = row.int("antall")
                    resultat to count
                },
            ).toMap()

    fun numberOfForskuddstrekkWithTabelltrekkByTrekkodeMetrics(tx: TransactionalSession): Map<no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Trekkode, Int> =
        tx
            .list(
                queryOf(
                    """
                    SELECT trekk_kode, COUNT(1) AS antall 
                    FROM forskuddstrekk
                    WHERE type = 'trekktabell'
                    GROUP BY trekk_kode
                    """.trimIndent(),
                ),
                extractor = { row ->
                    val trekkode =
                        no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Trekkode
                            .fromValue(row.string("trekk_kode"))
                    val count = row.int("antall")
                    trekkode to count
                },
            ).toMap()

    fun numberOfSkattekortByTilleggsopplysningMetrics(tx: TransactionalSession): Map<no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Tilleggsopplysning, Int> =
        tx
            .list(
                queryOf(
                    """
                    SELECT opplysning, COUNT(skattekort_id) AS antall 
                    FROM skattekort_tilleggsopplysning
                    GROUP BY opplysning, skattekort_id
                    """.trimIndent(),
                ),
                extractor = { row ->
                    val opplysning =
                        no.nav.sokos.skattekort.api.skattekortpersonapi.v1.Tilleggsopplysning
                            .fromDomainModel(Tilleggsopplysning.fromValue(row.string("opplysning")))
                    val count = row.int("antall")
                    opplysning to count
                },
            ).toMap()

    fun numberOfFrikortMedUtenBeloepsgrense(tx: TransactionalSession): Map<String, Int> =
        tx
            .list(
                queryOf(
                    """
                    SELECT 
                       CASE WHEN
                          frikort_beloep IS NULL OR frikort_beloep = 0 THEN 'Ubegrenset'
                          ELSE 'Begrenset'
                       END AS begrensning,
                    COUNT(1) AS antall 
                    FROM forskuddstrekk
                    WHERE type = 'frikort'
                    GROUP BY
                      CASE WHEN
                          frikort_beloep IS NULL OR frikort_beloep = 0 THEN 'Ubegrenset'
                          ELSE 'Begrenset'
                      END 
                    """.trimIndent(),
                ),
                extractor = { row ->
                    val type = row.string("begrensning")
                    val count = row.int("antall")
                    type to count
                },
            ).toMap()
}
