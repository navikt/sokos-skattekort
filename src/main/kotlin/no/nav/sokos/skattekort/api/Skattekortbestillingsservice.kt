package no.nav.sokos.skattekort.api
import com.zaxxer.hikari.HikariDataSource
import jakarta.jms.Message
import kotliquery.sessionOf

import no.nav.sokos.skattekort.domain.Bestilling

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
class Skattekortbestillingsservice(
    db: HikariDataSource,
) {
    private val db: HikariDataSource = db

    fun taImotOppdrag(message: Message) {
        val message1 = (message as? jakarta.jms.TextMessage)!!
        println("Hello, world! Received message: ${message1.text} from Skattekortbestillingsservice")
        val bestilling = parse(message1.text)
        sessionOf(db).use {
            it.transaction {
                it.run(
                    kotliquery
                        .queryOf(
                            "INSERT INTO BESTILLING (FNR, INNTEKTSAAR) VALUES (?,?)",
                            bestilling.fnr,
                            bestilling.inntektYear,
                        ).asUpdate,
                )
            }
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
