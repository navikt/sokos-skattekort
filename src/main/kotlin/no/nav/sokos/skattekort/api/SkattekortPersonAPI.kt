package no.nav.sokos.skattekort.api

import com.auth0.jwt.JWT
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import no.nav.sokos.skattekort.api.skattekortpersonapi.v1.SkattekortPersonRequest
import no.nav.sokos.skattekort.audit.Saksbehandler
import no.nav.sokos.skattekort.config.UnauthorizedException
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonService

fun Route.skattekortPersonApi(skattekortPersonService: SkattekortPersonService) {
    route("/api/v1") {
        post("hent-skattekort") {
            val skattekortPersonRequest: SkattekortPersonRequest = call.receive()
            val saksbehandler = getSaksbehandler(call)
            call.respond(
                skattekortPersonService.hentSkattekortPerson(skattekortPersonRequest, saksbehandler),
            )
        }
    }
}

private const val JWT_CLAIM_NAVIDENT = "NAVident"

fun getSaksbehandler(call: ApplicationCall): Saksbehandler {
    val oboToken =
        call.request.headers["Authorization"]?.removePrefix("Bearer ")
            ?: throw UnauthorizedException("Could not get token from request header")
    val navIdent = getNAVIdentFromToken(oboToken)

    return Saksbehandler(navIdent)
}

private fun getNAVIdentFromToken(token: String): String {
    val decodedJWT = JWT.decode(token)
    return decodedJWT.claims[JWT_CLAIM_NAVIDENT]?.asString()
        ?: throw UnauthorizedException("Missing NAVident in private claims")
}
