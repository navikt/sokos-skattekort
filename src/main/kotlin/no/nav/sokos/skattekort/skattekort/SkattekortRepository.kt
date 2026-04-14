package no.nav.sokos.skattekort.skattekort

import kotlinx.datetime.toJavaLocalDate

import kotliquery.Query
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.skattekort.ReglerForInntektsaar.alleLovligeInntektsaarAaHenteSkattekortFor

object SkattekortRepository {
    fun insert(
        tx: TransactionalSession,
        skattekort: Skattekort,
    ): Long {
        val columns =
            mutableListOf(
                "generert_fra",
                "person_id",
                "utstedt_dato",
                "identifikator",
                "inntektsaar",
                "kilde",
                "resultatForSkattekort",
            )
        val values =
            mutableListOf(
                ":generertFra",
                ":personId",
                ":utstedtDato",
                ":identifikator",
                ":inntektsaar",
                ":kilde",
                ":resultatForSkattekort",
            )

        val params =
            mutableMapOf<String, Any?>(
                "generertFra" to skattekort.generertFra?.value,
                "personId" to skattekort.personId.value,
                "utstedtDato" to skattekort.utstedtDato?.toJavaLocalDate(),
                "identifikator" to skattekort.identifikator,
                "inntektsaar" to skattekort.inntektsaar,
                "kilde" to skattekort.kilde,
                "resultatForSkattekort" to skattekort.resultatForSkattekort.value,
            )

        skattekort.id?.value?.let { explicitId ->
            columns.add(0, "id")
            values.add(0, ":id")
            params["id"] = explicitId
        }

        val id =
            tx.updateAndReturnGeneratedKey(
                Query(
                    statement =
                        """
                        INSERT INTO skattekort (${columns.joinToString(", ")})
                        VALUES (${values.joinToString(", ")})
                        """.trimIndent(),
                    paramMap = params,
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
        personId: PersonId,
        inntektsaar: Int?,
        adminRole: Boolean,
    ): List<Skattekort> {
        val hentFor = if (inntektsaar != null) listOf(inntektsaar) else alleLovligeInntektsaarAaHenteSkattekortFor()
        val inParams = List(hentFor.size) { idx -> ":inntektsaar$idx" }.joinToString(", ")
        val paramMap = hentFor.mapIndexed { idx, value -> "inntektsaar$idx" to value }.toMap() + ("personId" to personId.value)

        return tx.list(
            queryOf(
                """
                SELECT * FROM skattekort 
                WHERE person_id = :personId AND inntektsaar IN ($inParams)
                ORDER BY opprettet DESC, id DESC
                """.trimIndent(),
                paramMap,
            ),
            extractor = { row ->
                val id = SkattekortId(row.long("id"))
                Skattekort(row, findAllForskuddstrekkBySkattekortId(tx, id, adminRole = adminRole), findAllTilleggsopplysningBySkattekortId(tx, id, adminRole))
            },
        )
    }

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
            )

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
            )

    fun findLatestByPersonId(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
        adminRole: Boolean,
    ): Skattekort = findAllByPersonId(tx, personId, inntektsaar, adminRole).first()

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

    fun getManueltGenerertSkattekort(tx: TransactionalSession): List<Skattekort> {
        val adminRole = false
        return tx.list(
            queryOf(
                """
                SELECT s.* from skattekort s
                    WHERE s.resultatforskattekort = 'ikkeTrekkplikt' and s.kilde != 'manuell'
                      and s.generert_fra is null
                      and (not exists(select 1 from skattekort where generert_fra=s.id))
                    UNION
                    select * from skattekort s
                    where s.resultatforskattekort != 'ikkeTrekkplikt'  and s.kilde != 'manuell'
                      and EXISTS (SELECT 1
                                  FROM skattekort_tilleggsopplysning kpp
                                  WHERE kpp.skattekort_id = s.id
                                    AND kpp.opplysning = 'kildeskattPaaPensjon')
                      AND NOT (EXISTS (SELECT 1
                                       FROM forskuddstrekk ufore
                                       WHERE ufore.skattekort_id = s.id
                                         AND ufore.trekk_kode = 'ufoeretrygdFraNAV')
                        AND EXISTS (SELECT 1
                                    FROM forskuddstrekk pensjon
                                    WHERE pensjon.skattekort_id = s.id
                                      AND pensjon.trekk_kode = 'pensjonFraNAV'))
                      and (not exists(select 1 from skattekort where generert_fra=s.id))
                    UNION
                    SELECT s.* FROM skattekort_tilleggsopplysning ops
                                        join skattekort s on s.id = ops.skattekort_id
                    WHERE ops.skattekort_id = s.id  AND ops.opplysning = 'oppholdPaaSvalbard'
                      and (not exists(select 1 from skattekort where generert_fra=s.id))  and s.kilde != 'manuell'
                      and s.generert_fra is null;
                """.trimIndent(),
            ),
            extractor = { row ->
                val id = SkattekortId(row.long("id"))
                Skattekort(row, findAllForskuddstrekkBySkattekortId(tx, id, adminRole = adminRole), findAllTilleggsopplysningBySkattekortId(tx, id, adminRole))
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
}
