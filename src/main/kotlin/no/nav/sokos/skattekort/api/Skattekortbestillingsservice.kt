package no.nav.sokos.skattekort.api

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf

import no.nav.sokos.skattekort.domain.Bestilling
import no.nav.sokos.skattekort.util.SQLUtils.transaction

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
class Skattekortbestillingsservice(
    private val db: HikariDataSource,
) {
    fun taImotOppdrag(message: String) {
        println("Hello, world! Received message: $message from Skattekortbestillingsservice")
        val bestilling = parse(message)
        db.transaction { session ->
            println("Inserting bestilling into database: $bestilling")
            session.update(
                queryOf(
                    "INSERT INTO BESTILLING (FNR, INNTEKTSAAR) VALUES (:fnr,:inntektsaar)",
                    mapOf(
                        "fnr" to bestilling.fnr,
                        "inntektsaar" to bestilling.inntektYear,
                    ),
                ),
            )
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
