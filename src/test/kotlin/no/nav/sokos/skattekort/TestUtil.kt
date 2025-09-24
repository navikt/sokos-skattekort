package no.nav.sokos.skattekort

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplicationBuilder

import no.nav.sokos.skattekort.listener.DbListener

object TestUtil {
    fun TestApplicationBuilder.configureTestEnvironment() {
        environment {
            System.setProperty("APPLICATION_ENV", "TEST")

            // Ensure container is started before accessing its properties
            if (!DbListener.container.isRunning) {
                DbListener.container.start()
            }

            config =
                MapApplicationConfig().apply {
                    // Database properties
                    put("application.databaseType", "POSTGRES")
                    put("application.postgres.username", DbListener.container.username)
                    put("application.postgres.password", DbListener.container.password)
                    put("application.postgres.name", DbListener.container.databaseName)
                    put("application.postgres.port", DbListener.container.firstMappedPort.toString())
                    put("application.postgres.host", DbListener.container.host)
                }
        }
    }
}
