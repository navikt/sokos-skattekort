package no.nav.sokos.skattekort.config

import java.net.ProxySelector

import kotlinx.serialization.json.Json

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import mu.KotlinLogging
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner
import org.apache.hc.core5.util.TimeValue

private val logger = KotlinLogging.logger {}

fun createHttpClient(): HttpClient =
    HttpClient(Apache5) {
        expectSuccess = true

        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
                setKeepAliveStrategy { response, context ->
                    val defaultStrategy = DefaultConnectionKeepAliveStrategy.INSTANCE
                    val duration = defaultStrategy.getKeepAliveDuration(response, context)
                    duration ?: TimeValue.ofSeconds(30)
                }
            }
        }

        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    explicitNulls = false
                },
            )
        }

        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(5)
            modifyRequest { request ->
                logger.warn { "$retryCount retry feilet mot: ${request.url}" }
            }
            exponentialDelay()
        }
    }.also { client ->
        Runtime.getRuntime().addShutdownHook(
            Thread {
                client.close()
            },
        )
    }
