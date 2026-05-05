package no.nav.sokos.skattekort.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

import no.nav.sokos.skattekort.config.jsonConfig

data class MockResponse(
    val path: String,
    val content: String = "",
    val statusCode: HttpStatusCode = HttpStatusCode.OK,
    val pathMatching: PathMatchType = PathMatchType.EXACT,
) {
    fun matches(encodedPath: String): Boolean =
        when (pathMatching) {
            PathMatchType.EXACT -> encodedPath == path
            PathMatchType.PREFIX -> encodedPath.startsWith(path)
        }
}

enum class PathMatchType { EXACT, PREFIX }

object MockHttpClient {
    private val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))

    fun getEngine(vararg mockResponses: MockResponse): MockEngine =
        MockEngine { request ->
            val response =
                mockResponses.find { it.matches(request.url.encodedPath) }
                    ?: error("No mock response configured for ${request.method.value} ${request.url.encodedPath}")
            if (response.statusCode.isSuccess()) {
                respond(response.content, response.statusCode, responseHeaders)
            } else {
                respondError(response.statusCode, response.content, responseHeaders)
            }
        }

    fun getClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json(jsonConfig) }
            expectSuccess = false
        }
}
