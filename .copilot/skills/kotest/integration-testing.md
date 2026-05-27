# Integration Testing

## Database integration tests

Use `DbListener` as a Kotest extension — it manages a TestContainers PostgreSQL instance and automatically truncates all tables after each test. Example pattern:

```kotlin
internal class MyServiceIntegrationTest : FunSpec({
    extensions(DbListener, WiremockListener)

    val dbService = MyDatabaseService(DbListener.dataSource)

    test("behandler 2 ubehandlede oppføringer") {
        DbListener.loadDataSet("database/my-service/two-pending-entries.sql")
        WiremockListener.wiremock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/api/submit"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody(successResponse("ref-123")))
        )

        val entries = dbService.getAllPending()
        entries.shouldHaveSize(2)

        val results = MyService(dbService).processAll(entries)

        results.shouldHaveSize(2)
        dbService.getAllPending().shouldBeEmpty()
    }
})
```

### Typical DbListener API

| Helper | Purpose |
|---|---|
| `extensions(DbListener)` | Registers TestContainers PostgreSQL |
| `DbListener.dataSource` | `DataSource` with Flyway migrations applied |
| `DbListener.loadDataSet("database/...")` | Loads SQL fixture from `src/test/resources/` |

`DbListener` truncates all tables (except `flyway_schema_history`) automatically in `afterEach` — no manual cleanup is needed.

## SFTP integration tests

```kotlin
internal class FtpServiceIntegrationTest : FunSpec({
    extensions(SftpListener)
    // ...
})
```

## Mock HTTP clients (WiremockListener)

Use `WiremockListener` to stub external HTTP services. The listener resets all stubs after each test:

```kotlin
internal class MyApiTest : FunSpec({
    extensions(DbListener, WiremockListener)

    test("ekstern tjeneste svarer med OK") {
        WiremockListener.wiremock.stubFor(
            WireMock.post(WireMock.urlPathMatching("/api/v1/.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                        .withStatus(HttpStatusCode.OK.value)
                        .withBody(successResponse("ref-123"))
                )
        )
        // ... test body
    }
})
```

For unit tests that don't need HTTP wiring, mock the client class directly:

```kotlin
val externalClientMock = mockk<ExternalClient> {
    coEvery { submit(any(), any()) } returns mockk(relaxed = true)
}
```

## Circuit breaker

Circuit breakers are registered in `Metrics.circuitBreakerRegistry`. To reset the circuit breaker for a specific client in tests, access it via the registry:

```kotlin
beforeEach {
    Metrics.circuitBreakerRegistry.circuitBreaker("skatteetaten").reset()
}
```

When an open breaker is expected (e.g. after multiple error responses), verify with `coVerify(exactly = 1) { ... }` that further calls were suppressed.
