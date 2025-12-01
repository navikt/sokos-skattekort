package no.nav.sokos.skattekort.infrastructure

import java.util.concurrent.TimeUnit

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.sokos.skattekort.TestUtil.configureTestModule
import no.nav.sokos.skattekort.infrastructure.FullNettyApplication.oauthServer

private fun Application.applicationTestModule() {
    configureTestModule(oauthServer)
}

object FullNettyApplication : AutoCloseable {
    const val PORT = 9090

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    val oauthServer: MockOAuth2Server = MockOAuth2Server().also { server -> server.start() }

    fun start() {
        if (!::server.isInitialized) {
            server = embeddedServer(Netty, PORT, module = Application::applicationTestModule).start()
        }
    }

    override fun close() {
        server.stop(100, 100, TimeUnit.MILLISECONDS)
        oauthServer.shutdown()
    }
}
