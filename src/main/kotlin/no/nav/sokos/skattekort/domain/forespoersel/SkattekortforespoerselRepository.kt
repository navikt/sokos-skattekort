package no.nav.sokos.skattekort.domain.forespoersel

import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import kotlinx.datetime.toKotlinLocalDate

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.skattekort.domain.person.Foedselsnummer
import no.nav.sokos.skattekort.domain.person.FoedselsnummerId
import no.nav.sokos.skattekort.domain.person.Person
import no.nav.sokos.skattekort.domain.person.PersonId
import no.nav.sokos.skattekort.domain.person.Personidentifikator

object SkattekortforespoerselRepository {
    fun insertBatch(
        tx: TransactionalSession,
        forespoerselId: Long,
        aar: Int,
        personListe: List<Person>,
    ) {
        tx.batchPreparedNamedStatement(
            """
            |INSERT INTO forespoersel_skattekort (forespoersel_id, person_id, aar)
            |VALUES (:forespoersel_id, :person_id, :aar)
            """.trimMargin(),
            personListe.map { person ->
                mapOf(
                    "forespoersel_id" to forespoerselId,
                    "person_id" to person.id!!.value,
                    "aar" to aar,
                )
            },
        )
    }

    fun getAllSkattekortforespoersel(tx: TransactionalSession): List<Skattekortforespoersel> =
        tx.list(
            queryOf(
                """
                |SELECT fs.id, fs.forespoersel_id, f.forsystem, f.opprettet, fs.aar, p.id AS person_id, p.flagget, pf.id AS person_fnr_id, pf.fnr, pf.gjelder_fom
                |FROM forespoersel_skattekort fs
                |LEFT JOIN forespoersel f ON f.id = fs.forespoersel_id
                |LEFT JOIN person p ON p.id = fs.person_id
                |LEFT JOIN LATERAL (
                |   SELECT id, gjelder_fom, fnr
                |   FROM person_fnr
                |   WHERE person_id = p.id
                |   ORDER BY gjelder_fom DESC, id DESC
                |   LIMIT 1
                |) pf ON TRUE 
                """.trimMargin(),
            ),
            mapToSkattekortforespoersel,
        )

    @OptIn(ExperimentalTime::class)
    private val mapToSkattekortforespoersel: (Row) -> Skattekortforespoersel = { row ->
        Skattekortforespoersel(
            id = SkattekortforespoerselId(row.long("id")),
            forespoersel =
                Forespoersel(
                    id = ForespoerselId(row.long("forespoersel_id")),
                    dataMottatt = "",
                    forsystem = Forsystem.fromValue(row.string("forsystem")),
                    opprettet = row.instant("opprettet").toKotlinInstant(),
                ),
            aar = row.int("aar"),
            person =
                Person(
                    id = PersonId(row.long("person_id")),
                    flagget = row.boolean("flagget"),
                    foedselsnummer =
                        Foedselsnummer(
                            id = FoedselsnummerId(row.long("person_fnr_id")),
                            personId = PersonId(row.long("person_id")),
                            fnr = Personidentifikator(row.string("fnr")),
                            gjelderFom = row.localDate("gjelder_fom").toKotlinLocalDate(),
                        ),
                ),
        )
    }
}
