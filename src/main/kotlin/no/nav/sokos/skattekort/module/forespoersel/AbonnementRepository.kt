package no.nav.sokos.skattekort.module.forespoersel

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import kotlinx.datetime.toKotlinLocalDate

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.module.person.Foedselsnummer
import no.nav.sokos.skattekort.module.person.FoedselsnummerId
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator

object AbonnementRepository {
    fun insert(
        tx: TransactionalSession,
        forespoerselId: Long,
        inntektsaar: Int,
        personId: Long,
    ): Long? =
        tx.updateAndReturnGeneratedKey(
            queryOf(
                """
                |INSERT INTO abonnementer (forespoersel_id, person_id, inntektsaar)
                |VALUES (:forespoerselId, :personId, :inntektsaar)
                """.trimMargin(),
                mapOf(
                    "forespoerselId" to forespoerselId,
                    "personId" to personId,
                    "inntektsaar" to inntektsaar,
                ),
            ),
        )

    fun getAllAbonnementer(tx: TransactionalSession): List<Abonnement> =
        tx.list(
            queryOf(
                """
                |SELECT fs.id, fs.forespoersel_id, f.forsystem, f.opprettet, f.batch, fs.inntektsaar, p.id AS person_id, p.flagget, pf.id AS person_fnr_id, pf.fnr, pf.gjelder_fom
                |FROM abonnementer fs
                |LEFT JOIN forespoersler f ON f.id = fs.forespoersel_id
                |LEFT JOIN personer p ON p.id = fs.person_id
                |LEFT JOIN LATERAL (
                |   SELECT id, gjelder_fom, fnr
                |   FROM foedselsnumre
                |   WHERE person_id = p.id
                |   ORDER BY gjelder_fom DESC, id DESC
                |   LIMIT 1
                |) pf ON TRUE 
                """.trimMargin(),
            ),
            mapToAbonnement,
        )

    fun findForsystemAndFnr(
        tx: TransactionalSession,
        personId: PersonId,
        inntektsaar: Int,
    ): List<Pair<Forsystem, Personidentifikator>> =
        tx.list(
            queryOf(
                """
                SELECT DISTINCT f.forsystem as forsystem,
                                (SELECT fn.fnr
                                 FROM abonnementer a
                                          INNER JOIN forespoersler f ON f.id = a.forespoersel_id
                                          INNER JOIN foedselsnumre fn ON a.person_id = fn.person_id
                                 WHERE a.person_id = :personId
                                   AND f.data_mottatt LIKE '%' || fn.fnr || '%'
                                 order by f.id desc
                                 limit 1) as fnr
                FROM abonnementer a
                         JOIN forespoersler f ON f.id = a.forespoersel_id
                WHERE a.person_id = :personId
                  AND a.inntektsaar = :inntektsaar;
                """.trimIndent(),
                mapOf(
                    "personId" to personId.value,
                    "inntektsaar" to inntektsaar,
                ),
            ),
            { row ->
                println()
                Pair(Forsystem.fromValue(row.string("forsystem")), Personidentifikator(row.string("fnr")))
            },
        )

    @OptIn(ExperimentalTime::class)
    private val mapToAbonnement: (Row) -> Abonnement = { row ->
        Abonnement(
            id = AbonnementId(row.long("id")),
            forespoersel =
                Forespoersel(
                    id = ForespoerselId(row.long("forespoersel_id")),
                    dataMottatt = "",
                    forsystem = Forsystem.fromValue(row.string("forsystem")),
                    opprettet = row.instant("opprettet").toKotlinInstant(),
                    batch = row.boolean("batch"),
                ),
            inntektsaar = row.int("inntektsaar"),
            person =
                Person(
                    id = PersonId(row.long("person_id")),
                    flagget = row.boolean("flagget"),
                    foedselsnummer =
                        Foedselsnummer(
                            id = FoedselsnummerId(row.long("id")),
                            personId = PersonId(row.long("person_id")),
                            fnr = Personidentifikator(row.string("fnr")),
                            gjelderFom = row.localDate("gjelder_fom").toKotlinLocalDate(),
                        ),
                ),
        )
    }
}
