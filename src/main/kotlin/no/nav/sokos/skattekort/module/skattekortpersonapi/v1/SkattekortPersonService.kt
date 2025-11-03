package no.nav.sokos.skattekort.module.skattekortpersonapi.v1

import io.ktor.server.application.ApplicationCall
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class SkattekortPersonService {
    fun hentSkattekortPerson(
        skattekortPersonRequest: SkattekortPersonRequest,
        applicationCall: ApplicationCall,
    ): List<SkattekortTilArbeidsgiver> = throw NotImplementedError()
}
