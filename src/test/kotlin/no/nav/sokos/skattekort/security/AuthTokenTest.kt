package no.nav.sokos.skattekort.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.skattekort.config.UnauthorizedException

class AuthTokenTest :
    FunSpec({
        test("token bør returnere NAVident") {
            val token = generateToken("aUser")
            AuthToken.getNAVIdentFromToken(token) shouldBe "aUser"
        }

        test("token uten NAVident kaster en RuntimeException") {
            val token = generateToken()
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.headers[HttpHeaders.Authorization] } returns token
            val result =
                shouldThrow<UnauthorizedException> {
                    AuthToken.authorizeAndGetMandatorySaksbehandler(call)
                }

            result.message shouldBe "Missing NAVident in private claims"
        }

        test("mangler token i header kaster en Error") {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.headers[HttpHeaders.Authorization] } returns null

            val exception =
                shouldThrow<UnauthorizedException> {
                    AuthToken.authorizeAndGetMandatorySaksbehandler(call)
                }

            exception.message shouldBe "Could not get token from request header"
        }
    })

private fun generateToken(navIdent: String? = null): String {
    val builder = JWT.create().withSubject("1234567890")
    if (navIdent != null) builder.withClaim(JWT_CLAIM_NAVIDENT, navIdent)
    return builder.sign(Algorithm.HMAC256("secret"))
}
