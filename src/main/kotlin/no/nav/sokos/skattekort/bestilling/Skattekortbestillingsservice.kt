package no.nav.sokos.skattekort.bestilling

import com.zaxxer.hikari.HikariDataSource
import jakarta.jms.Message
import jakarta.jms.TextMessage
import kotliquery.queryOf
import kotliquery.sessionOf

import no.nav.sokos.skattekort.aktoer.AktoerRepository

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
class Skattekortbestillingsservice(
    db: HikariDataSource,
) {
    private val db: HikariDataSource = db

    fun taImotOppdrag(message: Message) {
        val message1 = (message as? TextMessage)!!
        println("Hello, world! Received message: ${message1.text} from Skattekortbestillingsservice")
        val bestilling: Bestilling = parse(message1.text)
        sessionOf(db, returnGeneratedKey = true).use {
            it.transaction {
                val (aktoerId, opprettet) = AktoerRepository.findOrCreateByOffNr(it, bestilling.fnr, "Mottatt bestilling på skattekort")
                it.run(
                    queryOf(
                        "INSERT INTO bestillinger (aktoer_id, fnr, aar) VALUES (?,?,?)",
                        aktoerId.id, // FIXME
                        bestilling.fnr,
                        bestilling.inntektYear,
                    ).asUpdate,
                )
                // TODO: Opprett aktoer_audit: vi har laget bestilling
                // TODO: Opprett innslag i aktoer_bestiltfra
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
        return Bestilling(bestiller, inntektYear, fnr)
    }
}
