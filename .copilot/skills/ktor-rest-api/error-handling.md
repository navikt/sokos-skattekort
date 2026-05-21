# Error Handling

## StatusPages plugin

All error responses flow through the `StatusPages` plugin configured in `StatusPageConfig.kt`. Individual routes never catch-and-respond — they throw exceptions and let `StatusPages` translate them.

### Installation

```kotlin
// CommonConfig.kt
install(StatusPages) {
    statusPageConfig()
}
```

### Exception-to-HTTP mapping

```kotlin
fun StatusPagesConfig.statusPageConfig() {
    exception<Throwable> { call, cause ->
        val (responseStatus, apiError) =
            when (cause) {
                is BadRequestException -> {
                    val jsonException = cause.findCauseOfType<JsonConvertException>()
                    createApiError(HttpStatusCode.BadRequest, jsonException?.message ?: cause.message, call)
                }
                is ClientRequestException -> createApiError(cause.response.status, cause.message, call)
                is RequestValidationException -> createApiError(HttpStatusCode.BadRequest, cause.reasons.joinToString(), call)
                is IllegalArgumentException -> createApiError(HttpStatusCode.BadRequest, cause.message, call)
                is AuthenticationException -> createApiError(HttpStatusCode.Unauthorized, cause.message, call)
                is AuthorizationException -> createApiError(HttpStatusCode.Forbidden, cause.message, call)
                is BatchUpdateException -> createApiError(HttpStatusCode.InternalServerError, "En teknisk feil har oppstått. Ta kontakt med utviklerne, detaljer er logget til TEAM LOGS", call)
                else -> createApiError(HttpStatusCode.InternalServerError, cause.message ?: "En teknisk feil har oppstått. Ta kontakt med utviklerne", call)
            }
        call.respond(responseStatus, apiError)
    }
}
```

### Mapping rules

| Exception | HTTP Status | Notes |
|---|---|---|
| `BadRequestException` | 400 | Unwrap nested `JsonConvertException` for the message |
| `ClientRequestException` | Forward status | Propagate upstream HTTP status |
| `RequestValidationException` | 400 | Join all validation reasons |
| `IllegalArgumentException` | 400 | Covers `require()` / `check()` failures |
| `AuthenticationException` | 401 | Missing/invalid JWT |
| `AuthorizationException` | 403 | Missing scope or role |
| `BatchUpdateException` | 500 | Generic message — details in TEAM LOGS only |
| Everything else | 500 | Fallback with generic message |

## ApiError response format

Every error response uses the same JSON shape:

```kotlin
@Serializable
data class ApiError(
    val timestamp: @Contextual Instant,
    val status: Int,
    val error: String,
    val message: String?,
    val path: String,
)
```

Example response:

```json
{
    "timestamp": "2025-01-15T10:30:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "personIdent er ugyldig. Tillatt format er 11 siffer",
    "path": "/api/v1/skattekort/bestille"
}
```

## How routes use this

Routes simply throw the appropriate exception — `StatusPages` handles the rest:

```kotlin
// Validation failure → 400 via RequestValidationException (automatic from plugin)
// Auth failure → 403 via AuthorizationException
call.requirePermission(requiredScope = Scope.BESTILLE_SCOPE, requiredRole = Role.BESTILLE_ROLE)

// Precondition failure → 400 via IllegalArgumentException
val id = call.parameters["id"]?.toLongOrNull()
    ?: throw IllegalArgumentException("Ugyldig id, må være et tall")

// Explicit auth error → 403
throw AuthorizationException("Mangler rettigheter til å se informasjon!")
```

## Finding nested causes

Use the `findCauseOfType` helper to unwrap chained exceptions:

```kotlin
private inline fun <reified T : Throwable> Throwable.findCauseOfType(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
```

This is used to extract `JsonConvertException` from `BadRequestException` to provide a more specific error message.

## Adding new exception types

1. Create the exception class (e.g. in `security/` or domain package)
2. Add a new `is MyException ->` branch in `statusPageConfig()`
3. Map to the appropriate HTTP status code
4. Keep user-facing messages generic for 5xx — log details with `TEAM_LOGS_MARKER`
