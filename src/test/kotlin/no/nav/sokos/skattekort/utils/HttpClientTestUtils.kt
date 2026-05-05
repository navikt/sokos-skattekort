package no.nav.sokos.skattekort.utils

import java.net.ProxySelector

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import mu.KotlinLogging
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner
import org.apache.hc.core5.util.TimeValue

import no.nav.sokos.skattekort.config.jsonConfig

private val logger = KotlinLogging.logger {}

fun createTestHttpClient(): HttpClient =
    HttpClient(Apache5) {
        expectSuccess = false
        engine {
            socketTimeout = 30_000
            connectTimeout = 30_000
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
                setKeepAliveStrategy { _, _ -> TimeValue.ofSeconds(300) }
            }
        }

        install(ContentNegotiation) {
            json(jsonConfig)
        }

        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(5)
            modifyRequest { request ->
                logger.warn { "$retryCount retry feilet mot: ${request.url}" }
            }
            exponentialDelay()
        }
    }
