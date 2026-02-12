# Code Quality Analysis Report
**Project:** sokos-skattekort  
**Date:** 2026-02-12  
**Analysis Type:** Static Code Analysis & Architecture Review

## Executive Summary

This report provides a comprehensive analysis of the sokos-skattekort codebase, identifying code quality issues, security concerns, and architectural patterns. The application is a well-structured Kotlin/Ktor service with good test coverage and modern tooling, but contains several areas for improvement.

### Overall Assessment
- **Code Quality:** ⭐⭐⭐⚪⚪ (3/5)
- **Security:** ⭐⭐⭐⚪⚪ (3/5)
- **Architecture:** ⭐⭐⭐⭐⚪ (4/5)
- **Maintainability:** ⭐⭐⭐⚪⚪ (3/5)
- **Testing:** ⭐⭐⭐⭐⚪ (4/5)

---

## 1. Project Overview

### Technology Stack
- **Language:** Kotlin 2.3.0 (Java 25 JVM)
- **Framework:** Ktor 3.4.0
- **Database:** PostgreSQL with Flyway migrations
- **Messaging:** IBM MQ / ActiveMQ, Kafka
- **Testing:** Kotest 6.1.2, Testcontainers
- **Quality Tools:** ktlint 14.0.1, Kover 0.9.5 (code coverage)

### Codebase Metrics
- **Source Files:** 83 Kotlin files
- **Test Files:** 30 Kotlin files  
- **Lines of Code:** ~6,746 (main) + ~5,207 (test)
- **Test Coverage:** Kover configured (HTML reports enabled)
- **Code-to-Test Ratio:** ~1:0.77 (excellent)

---

## 2. Critical Issues 🔴

### 2.1 Debug Statement in Production Code
**File:** `src/main/kotlin/no/nav/sokos/skattekort/module/forespoersel/AbonnementRepository.kt:86`

```kotlin
{ row ->
    println()  // ⚠️ Debug statement left in production code
    Pair(Forsystem.fromValue(row.string("forsystem")), Personidentifikator(row.string("fnr")))
},
```

**Impact:** 
- Unnecessary I/O operations in production
- No logging context or structured data
- Performance overhead

**Recommendation:** Remove `println()` or replace with proper logging:
```kotlin
logger.debug { "Processing row for personId: ${row.string("fnr")}" }
```

---

### 2.2 Try-Catch Used for Control Flow
**File:** `src/main/kotlin/no/nav/sokos/skattekort/api/SkattekortPersonAPI.kt:124-131`

```kotlin
private fun erForskuddstrekkListeUgyldig(request: SkattekortPersonRequest) =
    try {
        request.skattekort.resultatForSkattekort?.let(ResultatForSkattekort::fromValue) != null
        request.skattekort.forskuddstrekkList.map { it.toDomainForskuddstrekk() }
        false
    } catch (e: Exception) {
        true
    }
```

**Issues:**
- Exception handling for validation is an anti-pattern
- Logic is inverted: exceptions indicate *valid* data
- Hides actual validation errors
- Catches all exceptions, including NPE

**Recommendation:** Use explicit validation:
```kotlin
private fun erForskuddstrekkListeUgyldig(request: SkattekortPersonRequest): Boolean {
    if (request.skattekort.resultatForSkattekort?.let { 
        runCatching { ResultatForSkattekort.fromValue(it) }.isFailure 
    } == true) return true
    
    return request.skattekort.forskuddstrekkList.any { 
        runCatching { it.toDomainForskuddstrekk() }.isFailure 
    }
}
```

---

### 2.3 Excessive Use of Non-Null Assertion (!!)
**Files:** Multiple locations in `BestillingService.kt`

```kotlin
// Line 69
val bestilling = bestillings.firstOrNull()!!  // ⚠️ Crashes if empty

// Line 143
val forskuddstrekk = response.arbeidsgiver!!.first()  // ⚠️ Multiple !!

// Lines 30, 134, etc. - 10+ occurrences
```

**Impact:**
- Runtime crashes with NullPointerException
- No meaningful error messages
- Difficult to debug in production

**Recommendation:** Use safe calls with meaningful defaults:
```kotlin
val bestilling = bestillings.firstOrNull() 
    ?: throw IllegalStateException("Expected at least one bestilling in batch $batchId")

val forskuddstrekk = response.arbeidsgiver?.firstOrNull()
    ?: throw IllegalStateException("Missing arbeidsgiver data for arbeidstaker ${arbeidstaker.identifikator}")
```

---

## 3. High-Priority Issues 🟡

### 3.1 Excessive Function Complexity
**File:** `BestillingService.kt`

Several functions exceed recommended complexity thresholds:

| Function | Lines | Complexity | Issues |
|----------|-------|------------|--------|
| `hentSkattekort()` | 125-253 (128 lines) | Very High | Deep nesting, multiple transactions, repetitive error handling |
| `haandterOppdateringsbestilling()` | 339-420 (81 lines) | High | Duplicate catch blocks, transaction management |
| `opprettBestillingsbatch()` | 49-122 (73 lines) | High | Nested lambdas, inner functions in transactions |

**Cyclomatic Complexity Estimate:**
- `hentSkattekort()`: ~25+ (target: <10)
- Contains 8 nested try-catch blocks with transaction management

**Recommendation:** Extract methods:
```kotlin
// Before: 128-line function with nested transactions
fun hentSkattekort() { /* 128 lines */ }

// After: Extract to smaller functions
private fun processSkattekortBatch(batch: BestillingBatch) { /* 20 lines */ }
private fun handleSkattekortResponse(response: Response) { /* 15 lines */ }
private fun updateSkattekortStatus(batch: BestillingBatch) { /* 10 lines */ }
```

---

### 3.2 Broad Exception Catching
**Files:** `BestillingService.kt`, `MaskinportenTokenClient.kt`

```kotlin
// Lines 110-114, 406-418, 443-446
catch (ex: Exception) {
    logger.error(TEAM_LOGS_MARKER, ex) { "Generic error" }
    throw ex
}

// MaskinportenTokenClient.kt
throw Exception("Feil fra tokenprovider...")
```

**Issues:**
- Catches all exceptions including fatal errors (OutOfMemoryError)
- No distinction between recoverable and non-recoverable errors
- Generic `Exception` thrown loses type information

**Recommendation:** Create custom exception hierarchy:
```kotlin
sealed class BestillingException(message: String, cause: Throwable? = null) 
    : RuntimeException(message, cause)

class SkattekortApiException(message: String, cause: Throwable? = null) 
    : BestillingException(message, cause)

class TransactionConflictException(message: String, cause: Throwable? = null) 
    : BestillingException(message, cause)

// Then catch specifically:
catch (ex: PSQLException) when (ex.message?.contains("serialize access") == true) {
    throw TransactionConflictException("Concurrent modification detected", ex)
}
```

---

### 3.3 Blocking Coroutines with runBlocking
**File:** `BestillingService.kt` - Lines 77, 136, 345, 423

```kotlin
runBlocking {
    personRepository.hentEllerOpprett(/* ... */)
}
```

**Impact:**
- Blocks threads in async operations
- Reduces throughput and scalability
- Defeats purpose of Kotlin coroutines

**Recommendation:** Make service methods suspending:
```kotlin
// Before
fun opprettBestillingsbatch() {
    runBlocking { personRepository.hentEllerOpprett(...) }
}

// After
suspend fun opprettBestillingsbatch() {
    personRepository.hentEllerOpprett(...)
}
```

---

### 3.4 Nested Transactions
**File:** `BestillingService.kt` - Lines 72-75, 189-203, 393-401

```kotlin
dataSource.transaction { tx ->
    // Outer transaction
    try {
        // Work
    } catch (ex: Exception) {
        dataSource.transaction { innerTx ->  // ⚠️ Nested transaction in catch
            // Cleanup work
        }
        throw ex
    }
}
```

**Issues:**
- Complex error recovery logic
- Difficult to test
- Potential deadlock scenarios
- Transaction isolation unclear

**Recommendation:** Extract cleanup to separate transaction:
```kotlin
fun processWithCleanup() {
    val result = try {
        dataSource.transaction { tx ->
            // Main work
        }
    } catch (ex: Exception) {
        performCleanup(batchId)  // Separate transaction
        throw ex
    }
}

private fun performCleanup(batchId: Int) {
    dataSource.transaction { tx ->
        // Cleanup logic
    }
}
```

---

## 4. Medium-Priority Issues 🟠

### 4.1 Inconsistent Logging

**Split Logging:**
```kotlin
// Lines 95-96, 207-208
logger.error { "Feil ved bestilling" }
logger.error(secureLogger) { "Detaljer: $details" }
```

**Markers Used Inconsistently:**
- Some errors use `TEAM_LOGS_MARKER`, others don't
- No clear policy on when to use markers

**Recommendation:**
- Consolidate related log statements
- Define clear logging policy
- Use structured logging with MDC context

```kotlin
logger.error(TEAM_LOGS_MARKER) {
    "Feil ved bestilling: ${ex.message}. " +
    "Detaljer er logget til secure log. BatchId: $batchId"
}
secureLogger.error(ex) { "Bestilling detaljer: $details" }
```

---

### 4.2 Poor Dependency Injection

**Constructor vs. Property Injection:**
```kotlin
class BestillingService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
) {
    // Direct dependency creation
    private val applicationProperties = PropertiesConfig.getApplicationProperties()
}
```

**Issues:**
- Direct instantiation makes testing harder
- Properties from global config instead of injection

**Recommendation:** Inject all dependencies:
```kotlin
class BestillingService(
    private val dataSource: DataSource,
    private val skatteetatenClient: SkatteetatenClient,
    private val featureToggles: UnleashIntegration,
    private val applicationProperties: ApplicationProperties,  // Injected
) {
    // All dependencies testable
}
```

---

### 4.3 Companion Object Metrics

**File:** `BestillingService.kt:450-455`

```kotlin
companion object {
    val oppdateringerMottattCounter = counter(
        name = "bestilling_oppdateringer_mottatt_total",
        // ...
    )
}
```

**Issues:**
- Tight coupling between metrics and class
- Difficult to mock in tests
- Static state

**Recommendation:** Extract to metrics service:
```kotlin
class BestillingMetrics {
    val oppdateringerMottattCounter = counter(/* ... */)
    val bestillingerFeiletCounter = counter(/* ... */)
}

class BestillingService(
    private val metrics: BestillingMetrics,
    // ...
)
```

---

## 5. Security Concerns 🔒

### 5.1 SQL Injection Risks (Low Risk)

**File:** `PersonRepository.kt:19-22`

```kotlin
val where = listOfNotNull(
    startId?.let { "p.id > :startId" },
).joinToString(" AND ").takeIf { it.isNotEmpty() }?.let { " WHERE $it" } ?: ""
```

**Analysis:**
- ✅ Uses parameterized queries (`:startId`)
- ⚠️ String concatenation pattern is fragile
- Risk is LOW but pattern could lead to issues if extended

**Recommendation:** Use query builder or ensure all string building is validated.

---

### 5.2 Weak Input Validation

**File:** `SkattekortPersonAPI.kt`

```kotlin
// Limited validation on FNR format
// No rate limiting visible
// No validation that personId matches authenticated user
```

**Recommendations:**
1. Add rate limiting per endpoint
2. Validate user permissions match requested personId
3. Add input sanitization for all user-supplied data

---

### 5.3 Feature Toggle Scattered Logic

**Multiple files:**
```kotlin
if (featureToggles.isBestillingerEnabled()) { /* ... */ }
if (featureToggles.isOppdateringerEnabled()) { /* ... */ }
```

**Issues:**
- Feature flags scattered throughout code
- No centralized feature management
- Difficult to track feature dependencies

**Recommendation:** Introduce feature flag service with clear documentation.

---

## 6. Architecture & Design ✅

### Strengths
✅ **Clear Module Boundaries:** Separate packages for api, module, infrastructure  
✅ **Repository Pattern:** Consistent data access pattern  
✅ **Service Layer:** Business logic separated from API  
✅ **Configuration Management:** Centralized in config package  
✅ **Test Infrastructure:** Comprehensive test setup with containers  

### Areas for Improvement
⚠️ **Service Layer Complexity:** BestillingService has too many responsibilities  
⚠️ **Transaction Management:** Mixed in service layer, should be abstracted  
⚠️ **Error Handling:** No consistent error handling strategy  
⚠️ **Async/Sync Mix:** `runBlocking` usage indicates unclear async boundaries  

---

## 7. Testing & Quality Tools 🧪

### Current Setup
- **Framework:** Kotest with JUnit 5
- **Code Coverage:** Kover 0.9.5 (HTML reports)
- **Linting:** ktlint 14.0.1 (auto-format on build)
- **CI/CD:** GitHub Actions with security scanning
- **Database:** Flyway migrations with Squawk linting

### Coverage Analysis
```
Test-to-Code Ratio: 1:0.77 (5207 test lines / 6746 main lines)
Coverage Tool: Kover (excludes generated PDL code)
Test Types: Unit, Integration, E2E, API contract tests
```

### Quality Gates (CI)
✅ Build & test on PR  
✅ CodeQL + Trivy security scan (weekly)  
✅ Database migration linting (Squawk)  
✅ ktlint check before merge  
⚠️ No coverage threshold enforced  

**Recommendation:** Add minimum coverage threshold (e.g., 80%) in CI.

---

## 8. Dependencies & Security 📦

### Dependency Management
- **Vulnerability Patching:** `lz4-java` patched via resolution strategy
- **IBM MQ Libraries:** Not tested automatically (manual testing required)
- **Dependabot:** Configured for automated updates
- **Security Scanning:** CodeQL + Trivy in CI

### Known Dependency Issues
```kotlin
// build.gradle.kts:151-160
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.lz4" && requested.name == "lz4-java") {
                useTarget("at.yawk.lz4:lz4-java:1.10.3")
                because("Prefer the patched fork for vulnerability fix")
            }
        }
    }
}
```

**Assessment:** Good proactive security patching ✅

---

## 9. Documentation 📚

### Available Documentation
- ✅ README.md (setup, deployment, development)
- ✅ Architecture docs (dokumentasjon/arkitektur/)
- ✅ Operations manual (dokumentasjon/drift/)
- ✅ Database migration docs
- ✅ Workflow documentation

### Missing Documentation
- ⚠️ API documentation (Swagger configured but no detailed docs)
- ⚠️ Error handling guidelines
- ⚠️ Logging standards
- ⚠️ Code contribution guidelines

---

## 10. Action Items & Recommendations

### Immediate Actions (1 week) 🔴
1. **Remove `println()` statement** (AbonnementRepository.kt:86)
2. **Fix try-catch control flow** in validation (SkattekortPersonAPI.kt)
3. **Replace top 5 !! operators** with safe calls and meaningful errors
4. **Add minimum test coverage threshold** to CI (80%)

### Short-term Actions (1 month) 🟡
5. **Extract complex functions** in BestillingService (3 functions > 70 lines)
6. **Create custom exception hierarchy** for domain errors
7. **Remove `runBlocking`** usage, make services suspending
8. **Consolidate logging patterns** and add structured logging
9. **Extract metrics to injectable service**

### Medium-term Actions (3 months) 🟠
10. **Refactor transaction management** - extract to transaction coordinator
11. **Improve error handling consistency** across services
12. **Add API documentation** with examples
13. **Implement rate limiting** on public endpoints
14. **Add monitoring for code complexity** in CI

### Long-term Actions (6 months) 🔵
15. **Introduce CQRS pattern** for complex queries
16. **Add distributed tracing** with OpenTelemetry spans
17. **Implement saga pattern** for multi-step transactions
18. **Performance testing** and optimization

---

## 11. Conclusion

The sokos-skattekort codebase demonstrates **good architectural foundations** with clear module boundaries, comprehensive testing, and modern tooling. However, there are **significant code quality issues** in critical service classes that impact maintainability and reliability.

### Priority Focus Areas:
1. **Code Complexity:** Reduce function size and nesting in BestillingService
2. **Error Handling:** Implement consistent exception handling strategy  
3. **Async Patterns:** Remove blocking calls in async contexts
4. **Code Cleanliness:** Eliminate debug statements and unsafe null assertions

With focused refactoring efforts on these areas, the codebase can achieve excellent maintainability and reliability standards. The existing test infrastructure provides a strong safety net for making these improvements.

---

**Report Generated:** 2026-02-12  
**Analyzer:** GitHub Copilot Coding Agent  
**Confidence Level:** High (based on static analysis and architecture review)
