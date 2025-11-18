package no.nav.sokos.skattekort.infrastructure

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.DependencyRegistryKey

import no.nav.sokos.skattekort.TestUtil.configureDependenciesBehaviour
import no.nav.sokos.skattekort.TestUtil.configureShutdownBehavior
import no.nav.sokos.skattekort.TestUtil.testEnvironmentConfig
import no.nav.sokos.skattekort.module

private fun Application.applicationTestModule() {
    if (!attributes.contains(DependencyRegistryKey)) {
        install(DI) {
            configureShutdownBehavior()
        }
    }
    configureDependenciesBehaviour()
    module(testEnvironmentConfig())
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
