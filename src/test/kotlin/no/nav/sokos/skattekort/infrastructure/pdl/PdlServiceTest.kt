package no.nav.sokos.skattekort.infrastructure.pdl

import io.github.resilience4j.core.functions.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.tilgangsmaskinen.ProblemDetailResponse

class PdlServiceTest :
    FunSpec({
        extensions(listOf(WiremockListener))

        val auditLogger = mockk<AuditLogger>(relaxed = true)
        val pdlService: PdlService by lazy {
            PdlService(
                pdlClientService =
                    PdlClientService(
                        httpClient = createHttpClient(),
                        pdlUrl = WiremockListener.wiremock.baseUrl(),
                        azuredTokenClient = WiremockListener.azuredTokenClient,
                    ),
                tilgangsmaskinClientService =
                    TilgangsmaskinClientService(
                        httpClient = createHttpClient(),
                        tilgangsmaskinUrl = WiremockListener.wiremock.baseUrl(),
                        azuredTokenClient = WiremockListener.azuredTokenClient,
                    ),
                auditLogger = auditLogger,
            )
        }

        val ident = "12345678910"
        val saksbehandler = mockk<Saksbehandler> { every { this@mockk.ident } returns "Z123456" }

        test("returns left when access is denied and does not call PDL") {
            val problem = mockk<ProblemDetailResponse>()
            WiremockListener.wiremockTilgangsmaskinStub()
            coEvery { tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident) } returns problem

            val result = pdlService.getPersonNavn(ident, saksbehandler)

            result shouldBe Either.left(problem)
            coVerify(exactly = 0) { pdlClientService.getPersonNavnBulk(any()) }
            coVerify(exactly = 1) { auditLogger.auditLog(any()) }
        }

        test("returns right with formatted name when middle name is null") {
            coEvery { tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident) } returns null

            val navn =
                mockk<Any> {
                    every { this@mockk.javaClass.getMethod("getFornavn").invoke(this@mockk) } returns "Ola"
                    every { this@mockk.javaClass.getMethod("getMellomnavn").invoke(this@mockk) } returns null
                    every { this@mockk.javaClass.getMethod("getEtternavn").invoke(this@mockk) } returns "Nordmann"
                }
            val person =
                mockk<Any> {
                    every { this@mockk.javaClass.getMethod("getNavn").invoke(this@mockk) } returns listOf(navn)
                }

            @Suppress("UNCHECKED_CAST")
            coEvery { pdlClientService.getPersonNavnBulk(listOf(ident)) } returns mapOf(ident to person) as Map<String, Any>

            val result = pdlService.getPersonNavn(ident, saksbehandler)

            result shouldBe Either.right("Ola Nordmann")
            coVerify(exactly = 1) { pdlClientService.getPersonNavnBulk(listOf(ident)) }
            coVerify(exactly = 1) { auditLogger.auditLog(any()) }
        }

        test("returns right with formatted name when middle name is present") {
            coEvery { tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident) } returns null

            val navn =
                mockk<Any> {
                    every { this@mockk.javaClass.getMethod("getFornavn").invoke(this@mockk) } returns "Ola"
                    every { this@mockk.javaClass.getMethod("getMellomnavn").invoke(this@mockk) } returns "Mellom"
                    every { this@mockk.javaClass.getMethod("getEtternavn").invoke(this@mockk) } returns "Nordmann"
                }
            val person =
                mockk<Any> {
                    every { this@mockk.javaClass.getMethod("getNavn").invoke(this@mockk) } returns listOf(navn)
                }

            @Suppress("UNCHECKED_CAST")
            coEvery { pdlClientService.getPersonNavnBulk(listOf(ident)) } returns mapOf(ident to person) as Map<String, Any>

            val result = pdlService.getPersonNavn(ident, saksbehandler)

            result shouldBe Either.right("Ola Mellom Nordmann")
            coVerify(exactly = 1) { pdlClientService.getPersonNavnBulk(listOf(ident)) }
            coVerify(exactly = 1) { auditLogger.auditLog(any()) }
        }

        test("returns right with empty string when PDL returns person without any name entries") {
            coEvery { tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident) } returns null

            val person =
                mockk<Any> {
                    every { this@mockk.javaClass.getMethod("getNavn").invoke(this@mockk) } returns emptyList<Any>()
                }

            @Suppress("UNCHECKED_CAST")
            coEvery { pdlClientService.getPersonNavnBulk(listOf(ident)) } returns mapOf(ident to person) as Map<String, Any>

            val result = pdlService.getPersonNavn(ident, saksbehandler)

            result shouldBe Either.right("")
            coVerify(exactly = 1) { pdlClientService.getPersonNavnBulk(listOf(ident)) }
            coVerify(exactly = 1) { auditLogger.auditLog(any()) }
        }

        test("returns right with empty string when PDL response does not contain ident key") {
            coEvery { tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident) } returns null
            coEvery { pdlClientService.getPersonNavnBulk(listOf(ident)) } returns emptyMap()

            val result = pdlService.getPersonNavn(ident, saksbehandler)

            result shouldBe Either.right("")
            coVerify(exactly = 1) { pdlClientService.getPersonNavnBulk(listOf(ident)) }
            coVerify(exactly = 1) { auditLogger.auditLog(any()) }
        }

        test("always writes audit log before access check and PDL call") {
            coEvery { tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident) } returns null

            val navn =
                mockk<Any> {
                    every { this@mockk.javaClass.getMethod("getFornavn").invoke(this@mockk) } returns "Ola"
                    every { this@mockk.javaClass.getMethod("getMellomnavn").invoke(this@mockk) } returns null
                    every { this@mockk.javaClass.getMethod("getEtternavn").invoke(this@mockk) } returns "Nordmann"
                }
            val person =
                mockk<Any> {
                    every { this@mockk.javaClass.getMethod("getNavn").invoke(this@mockk) } returns listOf(navn)
                }

            @Suppress("UNCHECKED_CAST")
            coEvery { pdlClientService.getPersonNavnBulk(listOf(ident)) } returns mapOf(ident to person) as Map<String, Any>

            pdlService.getPersonNavn(ident, saksbehandler)

            coVerify(exactly = 1) { auditLogger.auditLog(any()) }
            coVerify(exactly = 1) { tilgangsmaskinClientService.checkSaksbehandlerAccess(saksbehandler.ident, ident) }
            coVerify(exactly = 1) { pdlClientService.getPersonNavnBulk(listOf(ident)) }
        }
    })
