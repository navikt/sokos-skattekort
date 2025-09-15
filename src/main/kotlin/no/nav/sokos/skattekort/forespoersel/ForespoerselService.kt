package no.nav.sokos.skattekort.forespoersel

import com.zaxxer.hikari.HikariDataSource
import jakarta.jms.Message
import jakarta.jms.TextMessage
import kotliquery.queryOf
import kotliquery.sessionOf

import no.nav.sokos.skattekort.bestilling.Bestilling
import no.nav.sokos.skattekort.person.PersonService

class ForespoerselService(
    val db: HikariDataSource,
    val personService: PersonService,
) {
    fun taImotForespoersel(message: Message) {
        val message1 = (message as? TextMessage)!!

        val forespoerselId =
            sessionOf(db, returnGeneratedKey = true).use {
                it.transaction {
                    ForespoerselRepository().create(it, Forsystem.fromMessage(message1.text), message1.text)
                }
            }

        val forespoersel = parse(message1.text)

        sessionOf(db, returnGeneratedKey = true).use {
            it.transaction {
                ForespoerselRepository().createSkattekortforespoersler(it, forespoerselId, forespoersel.inntektYear, forespoersel.persons)
            }
        }

        val bestilling: Bestilling =
            Bestilling(
                forespoersel.persons.first().id,
                forespoersel.forsystem.kode,
                forespoersel.inntektYear.toString(),
                forespoersel.persons
                    .first()
                    .fnrs
                    .first()
                    .fnr,
            )

        sessionOf(db, returnGeneratedKey = true).use {
            it.transaction {
                val (personId, result) = personService.findOrCreateByOffNr(personService.personRepository, it, bestilling.fnr, "Mottatt bestilling på skattekort")
                it.run(
                    queryOf(
                        "INSERT INTO bestillinger (person_id, fnr, aar) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                        personId.id, // FIXME
                        bestilling.fnr,
                        Integer.parseInt(bestilling.inntektYear),
                    ).asUpdate,
                )
                // TODO: Opprett person_audit: vi har laget bestilling
                // TODO: Opprett innslag i person_bestiltfra
            }
        }
    }

    private fun parse(message: String): Forespoersel {
        val parts = message.split(";")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid message format: $message")
        }
        val forsystem = Forsystem.fromValue(parts[0])
        val inntektYear = Integer.parseInt(parts[1])
        val fnrString = parts[2]

        sessionOf(db, returnGeneratedKey = true).use {
            it.transaction {
                personService.findOrCreateByOffNr(personService.personRepository, it, fnrString, "Mottatt forespørsel om skattekort fra $forsystem")
            }
        }

        val person =
            sessionOf(db, returnGeneratedKey = true).use {
                it.transaction {
                    personService.personRepository.internalFindPersonByFnr(it, fnrString)!!
                }
            }

        return Forespoersel(forsystem, inntektYear, listOf(person))
    }
}
