package no.nav.sokos.skattekort.security

import com.auth0.jwt.interfaces.Payload
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
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
    BehaviorSpec({
        Given("et kall med principal som inneholder NAV-ident") {
            When("NAV-ident hentes fra principal") {
                Then("skal NAV-ident returneres") {
                    val payload = mockk<Payload>()
                    every { payload.getClaim(JWT_CLAIM_NAVIDENT).asString() } returns "aUser"

                    val principal = mockk<JWTPrincipal>()
                    every { principal.payload } returns payload

                    val call = mockk<ApplicationCall>(relaxed = true)
                    every { call.principal<JWTPrincipal>() } returns principal
                    call.getNavIdentOrNull() shouldBe "aUser"
                }
            }
        }

        Given("et kall uten principal") {
            When("NAV-ident hentes") {
                Then("skal null returneres") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    every { call.principal<JWTPrincipal>() } returns null

                    call.getNavIdentOrNull() shouldBe null
                }
            }

            When("kallende system hentes") {
                Then("skal Unknown returneres") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    every { call.principal<JWTPrincipal>() } returns null

                    call.getCallingSystem() shouldBe "Unknown"
                }
            }

            When("requirePermission kalles") {
                Then("skal AuthenticationException kastes") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    every { call.principal<JWTPrincipal>() } returns null

                    val response =
                        shouldThrow<AuthenticationException> {
                            call.requirePermission(Scope.HENT_SKATTEKORT_SCOPE, Role.HENT_SKATTEKORT_ROLE)
                        }

                    response.message shouldBe "No principal found - authentication not configured"
                }
            }

            When("requireScope kalles") {
                Then("skal AuthenticationException kastes") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    every { call.principal<JWTPrincipal>() } returns null

                    val response =
                        shouldThrow<AuthenticationException> {
                            call.requireScope(Scope.HENT_SKATTEKORT_SCOPE)
                        }
                    response.message shouldBe "No principal found - authentication not configured"
                }
            }

            When("requireRole kalles") {
                Then("skal AuthenticationException kastes") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    every { call.principal<JWTPrincipal>() } returns null

                    val response =
                        shouldThrow<AuthenticationException> {
                            call.requireRole(Role.HENT_SKATTEKORT_ROLE)
                        }

                    response.message shouldBe "No principal found - authentication not configured"
                }
            }
        }

        Given("et kall med gyldig principal og nødvendige tilganger") {
            When("requirePermission kalles med gyldig scope eller rolle") {
                Then("skal det ikke kastes exception") {
                    val call = mockk<ApplicationCall>(relaxed = true)

                    val token = generateToken(scopes = Scope.entries.map { it.value }, roles = Role.entries.map { it.value })
                    every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

                    shouldNotThrowAny {
                        call.requirePermission(requiredScope = Scope.HENT_SKATTEKORT_SCOPE, requiredRole = Role.HENT_SKATTEKORT_ROLE)
                    }
                }
            }

            When("requireScope kalles med gyldig scope") {
                Then("skal det ikke kastes exception") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    val token = generateToken(scopes = Scope.entries.map { it.value })
                    every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

                    shouldNotThrowAny { call.requireScope(Scope.HENT_SKATTEKORT_SCOPE) }
                }
            }

            When("requireRole kalles med gyldig rolle") {
                Then("skal det ikke kastes exception") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    val token = generateToken(roles = Role.entries.map { it.value })
                    every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

                    shouldNotThrowAny { call.requireRole(Role.HENT_SKATTEKORT_ROLE) }
                }
            }
        }

        Given("et kall med principal men uten etterspurt tilgang") {
            When("requirePermission kalles med ukjent scope eller rolle") {
                Then("skal AuthorizationException kastes") {
                    val call = mockk<ApplicationCall>(relaxed = true)
                    val mockScope = mockk<Scope>(relaxed = true)
                    val mockRole = mockk<Role>(relaxed = true)

                    val token = generateToken(scopes = Scope.entries.map { it.value }, roles = Role.entries.map { it.value })
                    every { call.principal<JWTPrincipal>() } returns token.getPrincipal()

                    shouldThrow<AuthorizationException> {
                        call.requirePermission(mockScope, mockRole)
                    }.message shouldBe "Missing required scope or role"
                }
            }

            When("requireScope kalles med ukjent scope") {
                Then("skal AuthorizationException kastes") {
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
            }

            When("requireRole kalles med ukjent rolle") {
                Then("skal AuthorizationException kastes") {
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
            }
        }
    })
