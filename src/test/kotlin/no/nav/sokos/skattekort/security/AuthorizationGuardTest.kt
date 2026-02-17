package no.nav.sokos.skattekort.security

import com.auth0.jwt.interfaces.Payload
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.skattekort.security.AuthorizationGuard.getCallingSystem
import no.nav.sokos.skattekort.security.AuthorizationGuard.getNavIdentOrNull
import no.nav.sokos.skattekort.security.AuthorizationGuard.requirePermission
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireRole
import no.nav.sokos.skattekort.security.AuthorizationGuard.requireScope
import no.nav.sokos.skattekort.security.TokenTestUtils.generateToken
import no.nav.sokos.skattekort.security.TokenTestUtils.getPrincipal

class AuthorizationGuardTest :
    FunSpec({
        test("getNavIdentOrNull returns NAVident when present") {
            val payload = mockk<Payload>()
            every { payload.getClaim(JWT_CLAIM_NAVIDENT).asString() } returns "aUser"

            val principal = mockk<JWTPrincipal>()
            every { principal.payload } returns payload

            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.principal<JWTPrincipal>() } returns principal
            call.getNavIdentOrNull() shouldBe "aUser"
        }

        test("getNavIdentOrNull returns null when principal is missing") {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.principal<JWTPrincipal>() } returns null

            call.getNavIdentOrNull() shouldBe null
        }

        test("getCallingSystem returns Unknown when principal is missing") {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.principal<JWTPrincipal>() } returns null

            call.getCallingSystem() shouldBe "Unknown"
        }

        test("requirePermission with scope or role should not throw any exception") {
            val call = mockk<ApplicationCall>(relaxed = true)

            val token = generateToken(scopes = Scope.entries.map { it.value }, roles = Role.entries.map { it.value })
            every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

            shouldNotThrowAny { call.requirePermission(scope = Scope.HENT_SCOPE) }
            shouldNotThrowAny { call.requirePermission(role = Role.HENT_ROLE) }
        }

        test("requirePermission throws AuthenticationException when principal is missing") {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.principal<JWTPrincipal>() } returns null

            val response =
                shouldThrow<AuthenticationException> {
                    call.requirePermission(Scope.HENT_SCOPE)
                }

            response.message shouldBe "No principal found - authentication not configured"
        }

        test("requirePermission throws AuthorizationException with unknown scope or role") {
            val call = mockk<ApplicationCall>(relaxed = true)
            val mockScope = mockk<Scope>(relaxed = true)

            val token = generateToken(scopes = Scope.entries.map { it.value }, roles = Role.entries.map { it.value })
            every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

            shouldThrow<AuthorizationException> {
                call.requirePermission(mockScope)
            }.message shouldBe "Missing required scope"
        }

        test("requireScope should not throw any exception") {
            val call = mockk<ApplicationCall>(relaxed = true)
            val token = generateToken(scopes = Scope.entries.map { it.value })
            every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

            shouldNotThrowAny { call.requireScope(Scope.HENT_SCOPE) }
        }

        test("requireScope throws AuthenticationException when principal is missing") {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.principal<JWTPrincipal>() } returns null

            val response =
                shouldThrow<AuthenticationException> {
                    call.requireScope(Scope.HENT_SCOPE)
                }
            response.message shouldBe "No principal found - authentication not configured"
        }

        test("requireScope throws AuthorizationException with unknown scope") {
            val call = mockk<ApplicationCall>(relaxed = true)
            val mockScope = mockk<Scope>(relaxed = true)

            val token = generateToken(scopes = Scope.entries.map { it.value })
            every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

            val response =
                shouldThrow<AuthorizationException> {
                    call.requireScope(mockScope)
                }
            response.message shouldBe "Missing required scope"
        }

        test("requireRole should not throw any exception") {
            val call = mockk<ApplicationCall>(relaxed = true)
            val token = generateToken(roles = Role.entries.map { it.value })
            every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

            shouldNotThrowAny { call.requireRole(Role.HENT_ROLE) }
        }

        test("requireRole throws AuthenticationException when principal is missing") {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.principal<JWTPrincipal>() } returns null

            val response =
                shouldThrow<AuthenticationException> {
                    call.requireRole(Role.HENT_ROLE)
                }

            response.message shouldBe "No principal found - authentication not configured"
        }

        test("requireRole throws AuthorizationException with unknown role") {
            val call = mockk<ApplicationCall>(relaxed = true)
            val mockRole = mockk<Role>(relaxed = true)
            val token = generateToken(scopes = Scope.entries.map { it.value })
            every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

            val response =
                shouldThrow<AuthorizationException> {
                    call.requireRole(mockRole)
                }
            response.message shouldBe "Missing required role"
        }
    })
