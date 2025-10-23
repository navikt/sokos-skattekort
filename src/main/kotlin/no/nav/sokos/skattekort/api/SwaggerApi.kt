package no.nav.sokos.skattekort.api

import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Routing

fun Routing.swaggerApi() {
    swaggerUI(
        path = "api/v1/skattekort/docs",
        swaggerFile = "openapi/skattekort-v1-swagger.yaml",
    )
}
