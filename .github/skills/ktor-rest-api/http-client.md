# HTTP Client

## Client factory

A shared `createHttpClient()` factory produces the base `HttpClient`:

```kotlin
fun createHttpClient(): HttpClient =
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
```

### Key settings

| Setting | Value | Why |
|---|---|---|
| Engine | `Apache5` | HTTP/1.1 with proxy support (required on-prem) |
| `expectSuccess` | `false` | Non-2xx is handled explicitly per client, not thrown automatically |
| `HttpRequestRetry` | 5 retries, exponential backoff | Resilience against transient failures |
| `ContentNegotiation` | `kotlinx.serialization` JSON | Shared `jsonConfig` for consistent serialization |
| Proxy | `SystemDefaultRoutePlanner` | Picks up JVM proxy settings (WebProxy on NAIS) |
| Keep-alive | 300 seconds | Reuse connections to reduce latency |

## Client service pattern

Each external service gets its own client class in `infrastructure/<service>/`:

```
infrastructure/
├── skatteetaten/
│   └── SkatteetatenClient.kt
├── pdl/
│   └── PdlClientService.kt
├── tilgangsmaskin/
│   └── TilgangsmaskinClientService.kt
└── dare/
    └── UtsendingDareClientService.kt
```

### Constructor injection

Client services receive the shared `HttpClient`, a named URL string, and a named token client:

```kotlin
class SkatteetatenClient(
    private val httpClient: HttpClient,
    @Named(SKATTEETATEN_URL) private val skatteetatenUrl: String,
    private val maskinportenTokenClient: MaskinportenTokenClient,
)
```

Named constants for URLs and token clients are defined in `Application.kt`:

```kotlin
const val PDL_URL = "pdlUrl"
const val PDL_AZURED_TOKEN_CLIENT = "pdlAzuredTokenClient"
```

## Circuit breaker

Every outgoing client wraps calls in a Resilience4j `CircuitBreaker`:

```kotlin
companion object {
    val circuitBreaker =
        Metrics.circuitBreakerRegistry.circuitBreaker(
            "${METRICS_NAMESPACE}_skatteetatenClientCircuitBreaker",
            custom()
                .failureRateThreshold(10f)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofMinutes(5))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build(),
        )
}
```

Usage with `decorateSuspendFunction`:

```kotlin
suspend fun bestillSkattekort(request: BestillSkattekortRequest): BestillSkattekortResponse =
    circuitBreaker
        .decorateSuspendFunction {
            val response = httpClient.post("$baseUrl/api/endpoint") {
                contentType(ContentType.Application.Json)
                bearerAuth(tokenClient.getAccessToken())
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                throw RuntimeException("Feil: ${response.status.value} - ${response.bodyAsText()}")
            }
            response.body<BestillSkattekortResponse>()
        }.invoke()
```

### Circuit breaker conventions

- Register on the shared `Metrics.circuitBreakerRegistry` — metrics are auto-bound
- Name format: `${METRICS_NAMESPACE}_<clientName>CircuitBreaker`
- Place the circuit breaker in a `companion object` for singleton behavior
- Use `decorateSuspendFunction` for coroutine-friendly wrapping

## Authentication

Two token types for outgoing calls:

| Token type | Client class | Use case |
|---|---|---|
| Azure AD M2M | `AzuredTokenClient` | Calling other NAV services |
| Maskinporten | `MaskinportenTokenClient` | Calling external APIs (e.g. Skatteetaten) |

```kotlin
// Azure AD — system-to-system within NAV
bearerAuth(azuredTokenClient.getSystemToken())

// Maskinporten — external service
bearerAuth(maskinportenTokenClient.getAccessToken())
```

## Response handling patterns

### Success or throw

```kotlin
if (!response.status.isSuccess()) {
    throw RuntimeException("Feil: ${response.status.value} - ${response.bodyAsText()}")
}
response.body<ResponseType>()
```

### Nullable response (e.g. 204 No Content)

```kotlin
if (response.status == HttpStatusCode.NoContent) {
    return null
}
```

### Status-based branching

```kotlin
return when (response.status) {
    HttpStatusCode.NoContent -> null
    HttpStatusCode.Forbidden -> {
        val problem = response.body<ProblemDetailResponse>()
        logger.warn(TEAM_LOGS_MARKER) { "Access denied: $problem" }
        problem
    }
    else -> throw ClientRequestException(response, "Uventet svar: ${response.status.value}")
}
```

### GraphQL response handling

For GraphQL clients (e.g. PDL), parse the response body and check for `errors`:

```kotlin
val result = response.body<GraphQLResponse<SomeQuery.Result>>()
if (result.errors?.isNotEmpty() == true) {
    throw PdlException(result.errors.joinToString { it.message })
}
result.data?.hentSomething.orEmpty()
```
