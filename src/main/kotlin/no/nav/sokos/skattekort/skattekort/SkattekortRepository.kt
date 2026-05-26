package no.nav.sokos.skattekort.skattekort

import kotlinx.serialization.json.Json

import kotliquery.Query
import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.intellij.lang.annotations.Language

import no.nav.sokos.skattekort.person.PersonId

object SkattekortRepository {
    fun insert(
        tx: TransactionalSession,
        skattekort: Skattekort,
    ): Long {
        @Language("PostgreSQL")
        val insertSkattekortSql =
            """
            INSERT INTO skattekort (generert_fra, person_id, utstedt_dato, identifikator, inntektsaar, kilde, resultatForSkattekort) 
            VALUES (:generertFra, :personId, :utstedtDato, :identifikator, :inntektsaar, :kilde, :resultatForSkattekort)
            """.trimIndent()
        val id =
            tx.updateAndReturnGeneratedKey(
                Query(
                    statement = insertSkattekortSql,
                    paramMap =
                        mapOf(
                            "generertFra" to skattekort.generertFra?.value,
                            "personId" to skattekort.personId.value,
                            "utstedtDato" to skattekort.utstedtDato,
                            "identifikator" to skattekort.identifikator,
                            "inntektsaar" to skattekort.inntektsaar,
                            "kilde" to skattekort.kilde,
                            "resultatForSkattekort" to skattekort.resultatForSkattekort.value,
                        ),
                ),
            )
        if (skattekort.forskuddstrekkList.isNotEmpty()) {
            @Language("PostgreSQL")
            val insertForskuddstrekkSql =
                """
                INSERT INTO forskuddstrekk (skattekort_id, trekk_kode, type, frikort_beloep, tabell_nummer, prosentsats, antall_mnd_for_trekk)
                VALUES (:skattekortId, :trekk_kode, :type, :frikort_beloep, :tabell_nummer, :prosentsats, :antall_mnd_for_trekk)
                """.trimIndent()
            tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                insertForskuddstrekkSql,
                skattekort.forskuddstrekkList.map { forskuddstrekk ->
                    when (forskuddstrekk) {
                        is Frikort -> {
                            mapOf(
                                "skattekortId" to id,
                                "trekk_kode" to forskuddstrekk.trekkode.value,
                                "type" to Forskuddstrekk.Companion.ForskuddstrekkType.FRIKORT.type,
                                "frikort_beloep" to forskuddstrekk.frikortBeloep,
                                "tabell_nummer" to null,
                                "prosentsats" to null,
                                "antall_mnd_for_trekk" to null,
                            )
                        }

                        is Prosentkort -> {
                            mapOf(
                                "skattekortId" to id,
                                "trekk_kode" to forskuddstrekk.trekkode.value,
                                "type" to Forskuddstrekk.Companion.ForskuddstrekkType.PROSENTKORT.type,
                                "frikort_beloep" to null,
                                "tabell_nummer" to null,
                                "prosentsats" to forskuddstrekk.prosentSats,
                                "antall_mnd_for_trekk" to null,
                            )
                        }

                        is Tabellkort -> {
                            mapOf(
                                "skattekortId" to id,
                                "trekk_kode" to forskuddstrekk.trekkode.value,
                                "type" to Forskuddstrekk.Companion.ForskuddstrekkType.TABELLKORT.type,
                                "frikort_beloep" to null,
                                "tabell_nummer" to forskuddstrekk.tabellNummer,
                                "prosentsats" to forskuddstrekk.prosentSats,
                                "antall_mnd_for_trekk" to forskuddstrekk.antallMndForTrekk,
                            )
                        }
                    }
                },
            )
        }
        if (skattekort.tilleggsopplysningList.isNotEmpty()) {
            @Language("PostgreSQL")
            val insertTilleggsopplysningSql =
                """
                INSERT INTO skattekort_tilleggsopplysning (skattekort_id, opplysning)
                VALUES (:skattekortId, :opplysning)
                """.trimIndent()
            tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                insertTilleggsopplysningSql,
                skattekort.tilleggsopplysningList.map { tilleggsopplysning ->
                    mapOf(
                        "skattekortId" to id,
                        "opplysning" to tilleggsopplysning.value,
                    )
                },
            )
        }
        return id ?: error("Failed to insert skattekort record")
    }

    fun findAllByPersonId(
        tx: TransactionalSession,
        personIdList: List<PersonId>,
        inntektsaarList: List<Int>,
        showOnlyLatest: Boolean = false,
        adminRole: Boolean = false,
    ): List<Skattekort> {
        val personIdParamList = List(personIdList.size) { idx -> ":personId$idx" }.joinToString(", ")
        val inntektsaarParamList = List(inntektsaarList.size) { idx -> ":inntektsaar$idx" }.joinToString(", ")
        val paramMap = personIdList.mapIndexed { idx, pid -> "personId$idx" to pid.value }.toMap() + inntektsaarList.mapIndexed { idx, i -> "inntektsaar$idx" to i }.toMap()
        val distinctQuery = if (showOnlyLatest) "DISTINCT ON (s.person_id)" else ""
        return tx
            .list(
                queryOf(
                    """            
                    SELECT $distinctQuery jsonb_build_object(
                                   'id', s.id,
                                   'generertFra', s.generert_fra,
                                   'personId', s.person_id,
                                   'utstedtDato', s.utstedt_dato,
                                   'identifikator', s.identifikator,
                                   'inntektsaar', s.inntektsaar,
                                   'kilde', s.kilde,
                                   'resultatForSkattekort', s.resultatForSkattekort,
                                   'opprettet', s.opprettet,
                                   'forskuddstrekkList',
                                   COALESCE(
                                           (SELECT jsonb_agg(jsonb_build_object(
                                                   'type', f.type,
                                                   'trekkode', f.trekk_kode,
                                                   'frikortBeloep', f.frikort_beloep,
                                                   'tabellNummer', f.tabell_nummer,
                                                   'prosentSats', f.prosentsats,
                                                   'antallMndForTrekk', f.antall_mnd_for_trekk))
                                            FROM forskuddstrekk f
                                            WHERE f.skattekort_id = s.id),
                                           '[]'::jsonb
                                   ),
                                   'tilleggsopplysningList',
                                   COALESCE(
                                           (SELECT jsonb_agg(t.opplysning)
                                            FROM skattekort_tilleggsopplysning t
                                            WHERE t.skattekort_id = s.id),
                                           '[]'::jsonb
                                   )
                           ) AS skattekort_json
                    FROM skattekort s
                    WHERE s.person_id IN ($personIdParamList)
                      AND s.inntektsaar IN ($inntektsaarParamList)
                    ORDER BY s.person_id, s.opprettet DESC, s.id DESC;            
                    """.trimIndent(),
                    paramMap,
                ),
            ) { row -> Json.decodeFromString<SkattekortJson>(row.string("skattekort_json")).toDomain() }
            .let { skattekortList ->
                if (adminRole) {
                    skattekortList
                } else {
                    skattekortList.map { skattekort ->
                        skattekort.copy(
                            forskuddstrekkList = skattekort.forskuddstrekkList.filter { !it.requiresAdminRole() },
                            tilleggsopplysningList = skattekort.tilleggsopplysningList.filter { !it.requiresAdminRole },
                        )
                    }
                }
            }
    }

    fun getAllIdByInntektsaar(
        tx: TransactionalSession,
        inntektsaar: Int,
    ): List<Long> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT id FROM skattekort WHERE inntektsaar = :inntektsaar ORDER BY id;
            """.trimIndent()
        return tx.list(
            queryOf(sql, mapOf("inntektsaar" to inntektsaar)),
            extractor = { row -> row.long("id") },
        )
    }

    fun deleteBatch(
        tx: TransactionalSession,
        skattekortIdList: List<Long>,
    ) {
        @Language("PostgreSQL")
        val sql = "DELETE FROM skattekort WHERE id = :skattekortId"
        tx.batchPreparedNamedStatement(
            sql,
            skattekortIdList.map { id ->
                mapOf("skattekortId" to id)
            },
        )
    }

    fun getSecondsSinceLatestSkattekortOpprettet(tx: TransactionalSession): Double? {
        @Language("PostgreSQL")
        val sql = "SELECT EXTRACT(EPOCH FROM NOW() - MAX(opprettet)) AS sekunder_siden_siste_skattekort FROM skattekort"
        return tx.single(
            queryOf(sql),
            extractor = { row -> row.doubleOrNull("sekunder_siden_siste_skattekort") },
        )
    }

    fun numberOfSkattekortByResultatForSkattekortMetrics(tx: TransactionalSession): Map<ResultatForSkattekort, Int> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT resultatForSkattekort, COUNT(1) AS antall 
            FROM skattekort
            GROUP BY resultatForSkattekort
            """.trimIndent()
        return tx
            .list(
                queryOf(sql),
                extractor = { row ->
                    val resultat = ResultatForSkattekort.fromValue(row.string("resultatForSkattekort"))
                    val count = row.int("antall")
                    resultat to count
                },
            ).toMap()
    }

    fun numberOfForskuddstrekkWithTabelltrekkByTrekkodeMetrics(tx: TransactionalSession): Map<String, Int> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT trekk_kode, COUNT(1) AS antall 
            FROM forskuddstrekk
            WHERE type = 'trekktabell'
            GROUP BY trekk_kode
            """.trimIndent()
        return tx
            .list(
                queryOf(sql),
                extractor = { row ->
                    val trekkode = row.string("trekk_kode")
                    val count = row.int("antall")
                    trekkode to count
                },
            ).toMap()
    }

    fun numberOfSkattekortByTilleggsopplysningMetrics(tx: TransactionalSession): Map<Tilleggsopplysning, Int> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT opplysning, COUNT(skattekort_id) AS antall 
            FROM skattekort_tilleggsopplysning
            GROUP BY opplysning
            """.trimIndent()
        return tx
            .list(
                queryOf(sql),
                extractor = { row ->
                    val opplysning =
                        Tilleggsopplysning.fromValue(row.string("opplysning"))
                    val count = row.int("antall")
                    opplysning to count
                },
            ).toMap()
    }

    fun numberOfFrikortMedUtenBeloepsgrense(tx: TransactionalSession): Map<String, Int> {
        @Language("PostgreSQL")
        val sql =
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
            """.trimIndent()
        return tx
            .list(
                queryOf(sql),
                extractor = { row ->
                    val type = row.string("begrensning")
                    val count = row.int("antall")
                    type to count
                },
            ).toMap()
    }

    fun getNoekkelinformasjon(tx: TransactionalSession): Map<String, Int> {
        @Language("PostgreSQL")
        val sql =
            """
            SELECT inntektsaar::text AS key, COUNT(1) AS antall FROM skattekort GROUP BY inntektsaar
            UNION ALL
            SELECT 'personer' AS key, COUNT(1) AS antall FROM personer;
            """.trimIndent()
        return tx
            .list(queryOf(sql)) { row -> row.string("key") to row.int("antall") }
            .toMap()
    }
}
