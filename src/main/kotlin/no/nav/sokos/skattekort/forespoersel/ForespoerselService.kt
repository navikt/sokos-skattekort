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
        println("Hello, world! Received message: ${message1.text} from ForespoerselService")

        val forespoerselId =
            sessionOf(db, returnGeneratedKey = true).use {
                it.transaction {
                    ForespoerselRepository().create(it, Forsystem.fromMessage(message1.text), message1.text)
                }
            }
        val bestilling: Bestilling = parse(message1.text)

        println("Lagret foresørsel med id $forespoerselId")

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

    private fun parse(message: String): Bestilling {
        val parts = message.split(";")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid message format: $message")
        }
        val bestiller = parts[0]
        val inntektYear = parts[1]
        val fnr = parts[2]
        println("Parsed message - Bestiller: $bestiller, Inntektsår: $inntektYear, Fnr: $fnr")
        return Bestilling(null, bestiller, inntektYear, fnr)
    }
}
