---
name: ktor-rest-api
description: "Ktor REST API patterns: server-side routing with extension functions, request validation, Swagger UI, HTTP clients with circuit breaker and retry, StatusPages error handling, and DTO conventions. Accepts prompts in Norwegian and English. (REST, API, ruter, endepunkter, HTTP-klient, feilhåndtering, Swagger)"
---

# Ktor REST API

REST patterns for this codebase — both the Ktor server routes and outgoing HTTP clients. Detailed examples live in the sub-files — load them on demand.

## Quick reference

### Server-side (incoming requests)

| Concept | Implementation |
|---|---|
| Route definition | Extension functions on `Route` (`fun Route.myApi(...)`) |
| Route mounting | Inside `authenticate(providerName) { ... }` block in `RoutingConfig` |
| Serialization | `kotlinx.serialization` with `@Serializable` data classes |
| Request validation | `RequestValidation` plugin with `validate<T>` blocks |
| Error responses | `StatusPages` plugin mapping exceptions → `ApiError` JSON |
| Access control | `call.requirePermission()` / `requireScope()` / `requireRole()` per endpoint |
| Swagger | `swaggerUI()` per API version, backed by OpenAPI YAML specs in `resources/openapi/` |
| API versioning | Path-based: `/api/v1/...`, `/api/v2/...` |
| JSON config | Shared `jsonConfig` (`prettyPrint`, `ignoreUnknownKeys`, `encodeDefaults`, `explicitNulls = false`) |

### Client-side (outgoing requests)

| Concept | Implementation |
|---|---|
| HTTP engine | Ktor `HttpClient(Apache5)` with proxy and keep-alive |
| Resilience | Resilience4j `CircuitBreaker` wrapping suspend functions |
| Retry | Ktor `HttpRequestRetry` plugin with exponential backoff |
| Auth | `bearerAuth()` with Azure AD or Maskinporten tokens |
| Content negotiation | `ContentNegotiation` with `kotlinx.serialization` JSON |
| Named dependencies | `@Named("clientName")` for URL and token client injection |

## Sub-files

- [server-routes.md](server-routes.md) — route extension functions, DTOs, request validation, Swagger UI
- [http-client.md](http-client.md) — HttpClient config, circuit breaker, token auth, retry
- [error-handling.md](error-handling.md) — StatusPages, ApiError, exception-to-HTTP mapping

## Boundaries

### Always
- Extension functions on `Route` for API definitions — never inline routes in `Application.module()`
- `@Serializable` data classes for request/response bodies
- Shared `jsonConfig` for consistent JSON behavior across server and client
- `StatusPages` for centralized error handling — never catch-and-respond in individual routes
- Circuit breaker on every outgoing HTTP client
- Bearer auth via `AzuredTokenClient` (M2M) or `MaskinportenTokenClient` (external)
- OpenAPI YAML specs in `src/main/resources/openapi/` — Swagger UI is auto-mounted
- Access control on every authenticated endpoint

### Never
- Return raw exceptions to the client — always go through `StatusPages`
- Build HTTP clients without retry and circuit breaker
- Use `expectSuccess = true` on clients where non-2xx is a valid business response
- Hardcode URLs — always inject via `@Named` string constants from `PropertiesConfig`
- Skip access control on authenticated routes
