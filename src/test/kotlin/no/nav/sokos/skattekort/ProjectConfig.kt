package no.nav.sokos.skattekort

import io.kotest.core.config.AbstractProjectConfig
import io.ktor.server.config.ApplicationConfig

import no.nav.sokos.skattekort.config.PropertiesConfig

/**
 * Laster konfigurasjon for hele Kotest-testsuiten før noen tester kjører. Dette går utenom den
 * vanlige `Application.module()`-lastingen (se `dokumentasjon/arkitektur/konfigurasjon.md`): her
 * lastes `application-test.conf` eksplisitt, uavhengig av hvilken `application.conf` som ellers
 * ligger på classpath.
 *
 * `application-test.conf` inneholder trygge, selvstendige testverdier for alt (databasenavn,
 * kø-navn osv.), i motsetning til `src/test/resources/application.conf`, som er ment å ligne på et
 * ekte Nais-miljø og forventer miljøvariabler for hemmeligheter.
 */
class ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        PropertiesConfig.load(ApplicationConfig("application-test.conf"))
    }
}
