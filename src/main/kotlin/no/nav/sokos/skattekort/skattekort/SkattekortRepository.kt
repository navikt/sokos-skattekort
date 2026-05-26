package no.nav.sokos.skattekort.skattekort

import kotlinx.serialization.json.Json

import kotliquery.Query
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.api.model.DetailStatus
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.Personidentifikator

object SkattekortRepository {
    fun insert(
        tx: TransactionalSession,
        skattekort: Skattekort,
    ): Long {
        val id =
            tx.updateAndReturnGeneratedKey(
                Query(
                    statement =
                        """
                        INSERT INTO skattekort (generert_fra, person_id, utstedt_dato, identifikator, inntektsaar, kilde, resultatForSkattekort) 
                        VALUES (:generertFra, :personId, :utstedtDato, :identifikator, :inntektsaar, :kilde, :resultatForSkattekort)
                        """.trimIndent(),
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
            tx.batchPreparedNamedStatementAndReturnGeneratedKeys(
                """
                INSERT INTO forskuddstrekk (skattekort_id, trekk_kode, type, frikort_beloep, tabell_nummer, prosentsats, antall_mnd_for_trekk)
                VALUES (:skattekortId, :trekk_kode, :type, :frikort_beloep, :tabell_nummer, :prosentsats, :antall_mnd_for_trekk)
                """.trimIndent(),
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
    ): List<Long> =
        tx.list(
            queryOf(
                """
                SELECT id FROM skattekort WHERE inntektsaar = :inntektsaar ORDER BY id;
                """.trimIndent(),
                mapOf("inntektsaar" to inntektsaar),
            ),
            extractor = { row -> row.long("id") },
        )

    fun deleteBatch(
        tx: TransactionalSession,
        skattekortIdList: List<Long>,
    ) {
        tx.batchPreparedNamedStatement(
            """
            DELETE FROM skattekort WHERE id = :skattekortId
            """.trimIndent(),
            skattekortIdList.map { id ->
                mapOf("skattekortId" to id)
            },
        )
    }

    fun getSecondsSinceLatestSkattekortOpprettet(tx: TransactionalSession): Double? =
        tx.single(
            queryOf(
                """SELECT EXTRACT(EPOCH FROM NOW() - MAX(opprettet)) AS sekunder_siden_siste_skattekort FROM skattekort""",
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

    fun numberOfForskuddstrekkWithTabelltrekkByTrekkodeMetrics(tx: TransactionalSession): Map<String, Int> =
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
                    val trekkode = row.string("trekk_kode")
                    val count = row.int("antall")
                    trekkode to count
                },
            ).toMap()

    fun numberOfSkattekortByTilleggsopplysningMetrics(tx: TransactionalSession): Map<Tilleggsopplysning, Int> =
        tx
            .list(
                queryOf(
                    """
                    SELECT opplysning, COUNT(skattekort_id) AS antall 
                    FROM skattekort_tilleggsopplysning
                    GROUP BY opplysning
                    """.trimIndent(),
                ),
                extractor = { row ->
                    val opplysning =
                        Tilleggsopplysning.fromValue(row.string("opplysning"))
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

    fun getNoekkelinformasjon(tx: TransactionalSession): Map<String, Int> =
        tx
            .list(
                queryOf(
                    """
            |SELECT inntektsaar::text AS key, COUNT(1) AS antall FROM skattekort GROUP BY inntektsaar
            |UNION ALL
            |SELECT 'personer' AS key, COUNT(1) AS antall FROM personer;
                    """.trimMargin(),
                ),
            ) { row -> row.string("key") to row.int("antall") }
            .toMap()

    fun getDetailStatus(
        tx: TransactionalSession,
        fnrs: Collection<Personidentifikator>,
    ): Map<String, DetailStatus> {
        if (fnrs.isEmpty()) {
            return emptyMap()
        }

        val valuesClause = fnrs.indices.map { Personidentifikator::value }.joinToString(", ") { idx -> "(:fnr$idx" }
        val paramMap = fnrs.mapIndexed { idx, value -> "fnr$idx" to value.value }.toMap()

        return tx
            .list(
                queryOf(
                    """
                    select sok.fnr /*                                                       */ as fnr,
                     sum(case when f.id is not null then 1 else 0 end) > 0 /*               */ as har_forespoersel,
                     string_agg(distinct concat(f.forsystem, a.inntektsaar::text), ', ') /* */ as abonnementer
                     sum(case when sminus1.id is not null then 1 else 0 end) > 0 /*         */ as skattekort_last_year,
                     sum(case when s.id is not null then 1 else 0 end) > 0 /*               */ as skattekort_this_year,
                     sum(case when splus1.id is not null then 1 else 0 end) > 0 /*          */ as skattekort_next_year,
                         from (values  $valuesClause) as sok(fnr)
                    left join foedselsnumre fnr on fnr.fnr = sok.fnr
                    left join abonnementer a on a.person_id = fnr.person_id
                    left join forespoersler f on f.id = a.forespoersel_id
                    left join skattekort sminus1 on sminus1.person_id = fnr.person_id and sminus1.inntektsaar = extract(year from current_date)::int - 1
                    left join skattekort s on s.person_id = fnr.person_id
                    left join skattekort splus1 on splus1.person_id = fnr.person_id and splus1.inntektsaar = extract(year from current_date)::int + 1

                    group by SOK.fnr;
                    """.trimIndent(),
                    paramMap,
                ),
            ) { row -> row.string("fnr") to mapToDetailStatus(row) }
            .toMap()
    }

    private val mapToDetailStatus: (Row) -> DetailStatus = { row ->
        DetailStatus(
            harForespoersel = row.boolean("forespoersel"),
            abonnements = row.stringOrNull("abonnements")?.split(", ")?.toList() ?: emptyList(),
            skattekortLastYear = row.boolean("skattekort_last_year"),
            skattekortThisYear = row.boolean("skattekort_this_year"),
            skattekortNextYear = row.boolean("skattekort_next_year"),
        )
    }
}
