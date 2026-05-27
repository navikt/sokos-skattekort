# Server Routes

## Route structure

Each API domain gets its own file with an extension function on `Route`:

```
api/
├── SkattekortApi.kt          # fun Route.skattekortApi(...)
├── SkattekortPersonApi.kt    # fun Route.skattekortPersonApi(...)
├── SkattekortAdminApi.kt     # fun Route.skattekortAdminApi(...)
├── SwaggerApi.kt             # fun Routing.swaggerApi()
└── model/
    ├── ForespoerselRequest.kt    # @Serializable DTO + RequestValidation
    ├── StatusResponse.kt         # @Serializable response DTO
    ├── WrappedWithErrorResponse.kt
    └── v1/ / v2/                 # Versioned DTOs when API evolves
```

## Defining routes

Routes are extension functions on `Route` that receive their service dependencies as parameters:

```kotlin
const val BASE_PATH = "/api/v1/skattekort"

fun Route.skattekortApi(
    forespoerselService: ForespoerselService,
    statusService: StatusService,
) {
    route(BASE_PATH) {
        post("bestille") {
            call.requirePermission(requiredScope = Scope.BESTILLE_SCOPE, requiredRole = Role.BESTILLE_ROLE)
            val request = call.receive<ForespoerselRequest>()
            val saksbehandler = call.getNavIdentOrNull()?.let { Saksbehandler(it) }
            forespoerselService.taImotForespoersel(message, saksbehandler)
            call.respond(HttpStatusCode.Created)
        }
    }
}
```

### Key conventions

1. **Base path constant** — define as `const val BASE_PATH_*` at file level
2. **Access control first** — every endpoint starts with `requirePermission`, `requireScope`, or `requireRole`
3. **Receive → process → respond** — consistent endpoint flow
4. **Extract saksbehandler** — `call.getNavIdentOrNull()?.let { Saksbehandler(it) }` for audit/logging
5. **PII logging** — use `TEAM_LOGS_MARKER` when logging request content containing personal data

## Mounting routes

All authenticated routes are mounted in `RoutingConfig.kt` inside `authenticate(...)`:

```kotlin
fun Application.routingConfig(applicationState: ApplicationState) {
    routing {
        internalNaisRoutes(applicationState)
        swaggerApi()
        authenticate(azureAdProperties.providerName) {
            val someService: SomeService by dependencies
            skattekortApi(someService)
        }
    }
}
```

Health/metrics routes (`internalNaisRoutes`) and Swagger are **outside** the `authenticate` block.

## Request/response DTOs

All DTOs use `kotlinx.serialization`:

```kotlin
@Serializable
data class ForespoerselRequest(
    val personIdent: String,
    val aar: Int,
    val forsystem: String,
)
```

### Wrapped response pattern

For APIs that need inline error messages (v2 pattern):

```kotlin
@Serializable
data class WrappedWithErrorResponse<T>(
    val data: T,
    val errorMessage: String? = null,
)
```

### DTO-from-domain pattern

Map domain objects to DTOs via companion `fromDomain`:

```kotlin
@Serializable
data class BestillingDTO(
    val id: Long,
    val status: String,
) {
    companion object {
        fun fromDomain(bestilling: Bestilling) = BestillingDTO(
            id = bestilling.id,
            status = bestilling.status.name,
        )
    }
}
```

## Request validation

Validation lives alongside the DTO definition using Ktor's `RequestValidation` plugin:

```kotlin
fun RequestValidationConfig.requestValidationSkattekortConfig() {
    validate<ForespoerselRequest> { request ->
        when {
            !isValidPersonIdent(request.personIdent) ->
                ValidationResult.Invalid("personIdent er ugyldig. Tillatt format er 11 siffer")
            !isValidAar(request.aar) ->
                ValidationResult.Invalid("Gyldig årstall er mellom ${Year.now().minusYears(1)} og inneværende år")
            else -> ValidationResult.Valid
        }
    }
}
```

Validation functions are installed in `CommonConfig`:

```kotlin
install(RequestValidation) {
    requestValidationSkattekortConfig()
    requestValidationUtsendingConfig()
}
```

## API versioning

Versioned APIs use path-based versioning (`/api/v1/...`, `/api/v2/...`). When the response shape changes, create a new route and a versioned DTO:

```
api/model/
├── v1/SkattekortDTO.kt    # Original shape
└── v2/SkattekortDTO.kt    # New shape with breaking changes
```

Both versions can coexist in the same route file, mounted under different path prefixes.

## Swagger UI

Each API version has its own OpenAPI spec and Swagger UI endpoint:

```kotlin
fun Routing.swaggerApi() {
    swaggerUI(
        path = "api/v1/skattekort/docs",
        swaggerFile = "openapi/skattekort-v1-swagger.yaml",
    )
}
```

OpenAPI specs live in `src/main/resources/openapi/`. Swagger routes are mounted **outside** `authenticate`.
