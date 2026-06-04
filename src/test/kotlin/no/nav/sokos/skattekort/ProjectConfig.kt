package no.nav.sokos.skattekort

import io.kotest.core.config.AbstractProjectConfig
import io.ktor.server.config.ApplicationConfig

import no.nav.sokos.skattekort.config.PropertiesConfig

class ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        PropertiesConfig.load(ApplicationConfig("application-test.conf"))
    }
}
