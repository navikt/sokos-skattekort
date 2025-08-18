package no.nav.sokos.lavendel.api
import com.zaxxer.hikari.HikariDataSource
import jakarta.jms.ConnectionFactory
import jakarta.jms.Message
import jakarta.jms.Queue
import kotliquery.sessionOf

import no.nav.sokos.lavendel.config.MQConfig
import no.nav.sokos.lavendel.domain.Bestilling

class Skattekortbestillingsservice(
    connectionFactory: ConnectionFactory = MQConfig.connectionFactory,
    bestilleSkattekortQueue: Queue,
    db: HikariDataSource,
) {
    private val connectionFactory: ConnectionFactory = connectionFactory
    private val bestilleSkattekortQueue: Queue = bestilleSkattekortQueue
    private val db: HikariDataSource = db

    fun taImotOppdrag(message: Message) {
        val message1 = (message as? jakarta.jms.TextMessage)!!
        println("Hello, world! Received message: ${message1.text} from Skattekortbestillingsservice")
        val bestilling = parse(message1.text)
        sessionOf(db).use {
            val value =
                it.transaction {
                    it.run(
                        kotliquery
                            .queryOf(
                                "INSERT INTO SKATTEKORT_BESTILLING (FNR, INNTEKTSAAR) VALUES (?,?)",
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
