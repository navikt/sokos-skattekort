package no.nav.sokos.skattekort.infrastructure

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine

import no.nav.sokos.skattekort.TestUtil
import no.nav.sokos.skattekort.TestUtil.overriddenTestComponents
import no.nav.sokos.skattekort.module

private fun Application.applicationTestModule() {
    overriddenTestComponents()
    module(TestUtil.testEnvironmentConfig())
}

object FullNettyApplication {
    const val PORT = 9090

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    fun start() {
        if (!::server.isInitialized) {
            println("CAll!!!!!!")
            server = embeddedServer(Netty, PORT, module = Application::applicationTestModule).start()
        }
    }
}
