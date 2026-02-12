# Quality Improvement Roadmap

This document provides a prioritized list of code quality improvements identified during the analysis of sokos-skattekort.

## Quick Wins (Can be fixed immediately) 🚀

### 1. Remove Debug Statement
**File:** `src/main/kotlin/no/nav/sokos/skattekort/module/forespoersel/AbonnementRepository.kt:86`

```diff
 { row ->
-    println()
     Pair(Forsystem.fromValue(row.string("forsystem")), Personidentifikator(row.string("fnr")))
 },
```

**Effort:** 5 minutes  
**Impact:** High (production code cleanliness)

---

### 2. Fix Exception-Based Validation
**File:** `src/main/kotlin/no/nav/sokos/skattekort/api/SkattekortPersonAPI.kt:124-131`

```diff
 private fun erForskuddstrekkListeUgyldig(request: SkattekortPersonRequest) =
-    try {
-        request.skattekort.resultatForSkattekort?.let(ResultatForSkattekort::fromValue) != null
-        request.skattekort.forskuddstrekkList.map { it.toDomainForskuddstrekk() }
-        false
-    } catch (e: Exception) {
-        true
-    }
+    request.skattekort.resultatForSkattekort?.let { 
+        runCatching { ResultatForSkattekort.fromValue(it) }.isFailure 
+    } == true || request.skattekort.forskuddstrekkList.any { 
+        runCatching { it.toDomainForskuddstrekk() }.isFailure 
+    }
```

**Effort:** 15 minutes  
**Impact:** High (correctness, readability)

---

### 3. Replace Unsafe Null Assertions
**Files:** Multiple locations in `BestillingService.kt`

```diff
-val bestilling = bestillings.firstOrNull()!!
+val bestilling = bestillings.firstOrNull() 
+    ?: throw IllegalStateException("Expected at least one bestilling in batch $batchId")

-val forskuddstrekk = response.arbeidsgiver!!.first()
+val forskuddstrekk = response.arbeidsgiver?.firstOrNull()
+    ?: throw IllegalStateException("Missing arbeidsgiver for arbeidstaker ${arbeidstaker.identifikator}")
```

**Effort:** 1 hour (10+ occurrences)  
**Impact:** High (runtime stability)

---

## Short-term Improvements (1-2 weeks) 🔨

### 4. Extract Complex Functions
**File:** `BestillingService.kt`

Split `hentSkattekort()` (128 lines) into smaller functions:

```kotlin
// Current: Single 128-line function
fun hentSkattekort() { /* 128 lines of complex logic */ }

// Proposed: Multiple focused functions
private fun hentSkattekort() {
    val batches = fetchReadyBatches()
    batches.forEach { batch ->
        processSkattekortBatch(batch)
    }
}

private fun processSkattekortBatch(batch: BestillingBatch) {
    val response = fetchSkattekortFromApi(batch)
    updateSkattekortInDatabase(batch, response)
    handleBatchCompletion(batch)
}

private fun fetchSkattekortFromApi(batch: BestillingBatch): SkattekortResponse { /* ... */ }
private fun updateSkattekortInDatabase(batch: BestillingBatch, response: SkattekortResponse) { /* ... */ }
private fun handleBatchCompletion(batch: BestillingBatch) { /* ... */ }
```

**Functions to refactor:**
- `hentSkattekort()` → 3-4 smaller functions
- `haandterOppdateringsbestilling()` → 2-3 smaller functions  
- `opprettBestillingsbatch()` → 2-3 smaller functions

**Effort:** 2-3 days  
**Impact:** Very High (maintainability, testability)

---

### 5. Create Custom Exception Hierarchy
**New file:** `src/main/kotlin/no/nav/sokos/skattekort/module/skattekort/BestillingExceptions.kt`

```kotlin
sealed class BestillingException(message: String, cause: Throwable? = null) 
    : RuntimeException(message, cause)

class SkattekortApiException(message: String, cause: Throwable? = null) 
    : BestillingException(message, cause)

class TransactionConflictException(message: String, cause: Throwable? = null) 
    : BestillingException(message, cause)

class ValidationException(message: String, cause: Throwable? = null) 
    : BestillingException(message, cause)

class DataNotFoundException(message: String) 
    : BestillingException(message)
```

Then replace generic exception handling:

```diff
-catch (ex: Exception) {
+catch (ex: PSQLException) when (ex.message?.contains("serialize access") == true) {
+    throw TransactionConflictException("Concurrent modification detected", ex)
+} catch (ex: SQLException) {
+    throw BestillingException("Database error", ex)
+}
```

**Effort:** 1 day  
**Impact:** High (error handling, debugging)

---

### 6. Remove runBlocking Usage
**File:** `BestillingService.kt`

Make service methods suspending:

```diff
-fun opprettBestillingsbatch() {
+suspend fun opprettBestillingsbatch() {
     if (featureToggles.isBestillingerEnabled()) {
-        runBlocking {
-            personRepository.hentEllerOpprett(/* ... */)
-        }
+        personRepository.hentEllerOpprett(/* ... */)
     }
 }
```

**Effort:** 1-2 days (requires updating callers)  
**Impact:** High (performance, scalability)

---

## Medium-term Improvements (1 month) 🏗️

### 7. Improve Logging Consistency

Create logging standards document and apply consistently:

```kotlin
// Standard pattern for errors
logger.error(TEAM_LOGS_MARKER) { 
    "Operation failed: ${ex.message}. Context: $context" 
}
secureLogger.error(ex) { "Sensitive details: $sensitiveData" }

// Use MDC for correlation
MDC.put("batchId", batchId.toString())
MDC.put("correlationId", correlationId)
try {
    // Business logic
} finally {
    MDC.clear()
}
```

**Effort:** 3-4 days  
**Impact:** Medium (debugging, monitoring)

---

### 8. Extract Metrics to Service

```kotlin
// New file
class BestillingMetrics {
    val oppdateringerMottattCounter = counter(
        name = "bestilling_oppdateringer_mottatt_total",
        description = "Antall oppdateringsbestillinger mottatt"
    )
    
    val bestillingerFeiletCounter = counter(
        name = "bestilling_feilet_total",
        description = "Antall bestillinger som feilet"
    )
    
    fun recordOppdateringMottatt() {
        oppdateringerMottattCounter.increment()
    }
}

// In BestillingService
class BestillingService(
    private val metrics: BestillingMetrics,
    // ...
) {
    fun haandterOppdateringsbestilling() {
        metrics.recordOppdateringMottatt()
    }
}
```

**Effort:** 2 days  
**Impact:** Medium (testability, separation of concerns)

---

### 9. Refactor Transaction Management

Extract transaction logic to coordinator:

```kotlin
class TransactionCoordinator(private val dataSource: DataSource) {
    
    fun <T> executeWithCleanup(
        action: (TransactionalSession) -> T,
        cleanup: ((Exception) -> Unit)? = null
    ): T = try {
        dataSource.transaction { tx ->
            action(tx)
        }
    } catch (ex: Exception) {
        cleanup?.invoke(ex)
        throw ex
    }
}

// Usage
transactionCoordinator.executeWithCleanup(
    action = { tx -> /* main work */ },
    cleanup = { ex -> /* cleanup on failure */ }
)
```

**Effort:** 3-4 days  
**Impact:** High (error handling, testability)

---

## Long-term Improvements (3-6 months) 📈

### 10. Add API Documentation

Generate comprehensive OpenAPI documentation:

```kotlin
// Add to all API endpoints
@OpenAPIDoc(
    summary = "Hent skattekort for person",
    description = "Returnerer alle skattekort for en gitt person og inntektsår",
    responses = [
        OpenAPIResponse(200, "Success", SkattekortResponse::class),
        OpenAPIResponse(404, "Person not found"),
        OpenAPIResponse(500, "Internal error")
    ]
)
get("/person/{personId}/skattekort") { /* ... */ }
```

**Effort:** 1-2 weeks  
**Impact:** Medium (developer experience)

---

### 11. Implement Rate Limiting

```kotlin
// Add rate limiting middleware
val rateLimiter = RateLimiter(
    maxRequests = 100,
    windowDuration = 1.minutes
)

routing {
    authenticate("azure") {
        rateLimit(rateLimiter) {
            get("/person/{personId}/skattekort") { /* ... */ }
        }
    }
}
```

**Effort:** 1 week  
**Impact:** High (security, stability)

---

### 12. Add Code Complexity Monitoring

Configure complexity checks in CI:

```kotlin
// build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.0"
}

detekt {
    config = files("config/detekt.yml")
    buildUponDefaultConfig = true
}
```

```yaml
# config/detekt.yml
complexity:
  active: true
  CyclomaticComplexMethod:
    threshold: 15
  LongMethod:
    threshold: 60
  LongParameterList:
    threshold: 6
```

**Effort:** 2-3 days  
**Impact:** High (prevent future technical debt)

---

## Testing Improvements 🧪

### 13. Add Coverage Threshold

```kotlin
// build.gradle.kts
kover {
    reports {
        verify {
            rule {
                minBound(80)  // 80% minimum coverage
            }
        }
    }
}
```

**Effort:** 1 hour  
**Impact:** Medium (quality assurance)

---

### 14. Add Mutation Testing

```kotlin
// build.gradle.kts
plugins {
    id("info.solidsoft.pitest") version "1.15.0"
}

pitest {
    targetClasses = setOf("no.nav.sokos.skattekort.*")
    threads = 4
    outputFormats = setOf("HTML", "XML")
    mutators = setOf("DEFAULTS")
}
```

**Effort:** 1 day  
**Impact:** Medium (test effectiveness)

---

## Performance Improvements ⚡

### 15. Add Database Connection Pooling Monitoring

```kotlin
// Add metrics for HikariCP
hikariDataSource.apply {
    metricRegistry = prometheusRegistry
    healthCheckRegistry = healthCheckRegistry
}
```

**Effort:** 4 hours  
**Impact:** Medium (observability)

---

## Effort Summary

| Priority | Items | Total Effort | Expected Impact |
|----------|-------|--------------|-----------------|
| Quick Wins | 3 items | 2-3 hours | High |
| Short-term | 3 items | 5-7 days | Very High |
| Medium-term | 3 items | 10-12 days | High |
| Long-term | 6 items | 4-6 weeks | High |

## Implementation Strategy

### Phase 1: Immediate (Week 1)
- Remove debug statement
- Fix exception-based validation
- Replace top 10 unsafe null assertions
- Add test coverage threshold

### Phase 2: Refactoring (Weeks 2-4)
- Extract complex functions
- Create exception hierarchy
- Remove runBlocking
- Improve logging consistency

### Phase 3: Architecture (Month 2-3)
- Refactor transaction management
- Extract metrics service
- Add API documentation
- Implement rate limiting

### Phase 4: Tooling (Month 4-6)
- Add code complexity checks
- Implement mutation testing
- Add performance monitoring
- Continuous improvement process

---

**Last Updated:** 2026-02-12  
**Status:** Initial Assessment  
**Next Review:** After Phase 1 completion
