# Virtual Threads + StructuredTaskScope

### Spring Boot 4 | Java 25 | Structured Concurrency

[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge)](https://opensource.org/licenses/Apache-2.0)
[![Preview API](https://img.shields.io/badge/API-Preview_Feature-yellow?style=for-the-badge)](https://openjdk.org/jeps/505)

> Write simple, blocking, imperative code. Let the JVM handle massive concurrency for you.

A practical guide for Spring Boot 4 microservices demonstrating **Virtual Threads** and **StructuredTaskScope** with all 5 built-in **Joiner** strategies (JEP 505).

---

## Table of Contents

- [Why This Project?](#why-this-project)
- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [API Reference](#api-reference)
- [Joiner Strategies Deep Dive](#joiner-strategies-deep-dive)
- [Benefits & Use Cases](#benefits--use-cases)
- [Error Handling](#error-handling)
- [curl Test Commands](#curl-test-commands)
- [Java Features Used (8 → 25)](#java-features-used-8--25)
- [Configuration](#configuration)
- [Best Practices](#best-practices)
- [Running Tests](#running-tests)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)
- [Contributing](#contributing)
- [References](#references)
- [Author](#author)

---

## Why This Project?

Traditional Java concurrency has a fundamental problem: **platform threads are expensive**.

```
One thread = 1MB stack memory
10,000 threads = 10GB RAM (just for stacks!)
```

Reactive programming (WebFlux) solved the scalability problem but introduced complexity:

```java
// Reactive: hard to read, hard to debug
webClient.get().uri("/api/users/{id}", userId)
    .retrieve()
    .bodyToMono(User.class)
    .flatMap(user -> webClient.get().uri("/api/orders/{id}", userId).retrieve().bodyToFlux(Order.class))
    .map(order -> transform(order))
    .collectList()
    .subscribe(result -> sendResponse(result), error -> handleError(error));
```

**Virtual Threads + StructuredTaskScope** gives you the best of both worlds:

```java
// Virtual Threads: simple, blocking, scalable
try (var scope = StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())) {
    var user = scope.fork(() -> userService.getUser(userId));
    var orders = scope.fork(() -> orderService.getOrders(userId));
    scope.join();
    return buildResponse(user.get(), orders.get());
}
```

Same code. Same team. A fraction of the infrastructure cost.

---

## Overview

This project demonstrates the evolution of concurrent programming in Java:

| Approach | Concurrency Model | Code Style | Scalability | Thread Count |
|---|---|---|---|---|
| **Sequential** | One thread, one call at a time | Imperative | Limited | 1 per request |
| **CompletableFuture** | Thread pool + async chains | Functional/Chained | Limited by pool size | Pool size (e.g., 200) |
| **Virtual Threads + StructuredTaskScope** | JVM-managed, millions of threads | Imperative (blocking) | Scales with I/O, not threads | Millions |

### The Payoff

```
SEQUENTIAL — one thread, one call at a time
  Profile  5s | Orders  5s | Payments  5s  = 15s total

CONCURRENT — StructuredTaskScope forks all three, joins once
  Profile  5s | Orders  5s | Payments  5s  =  5s total  (3x faster)
```

### Visual Comparison

```
Platform Threads (before):
┌─────────────┐
│   Request   │──→ Thread 1 ──→ Profile (5s) ──→ Orders (5s) ──→ Payments (5s) ──→ Response
└─────────────┘   (blocked for 15s)

Virtual Threads (after):
┌─────────────┐
│   Request   │──→ Virtual Thread
└─────────────┘   ├──→ Profile (5s)   ─┐
                   ├──→ Orders (5s)   ─┼──→ Response (5s)
                   └──→ Payments (5s) ─┘
```

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| **Java** | 25+ | With `--enable-preview` flag |
| **Spring Boot** | 4.1.1+ | First release with virtual thread support |
| **Maven** | 3.9+ | Or use included wrapper (`./mvnw`) |
| **IDE** | IntelliJ / VS Code | With Java 25 support |

### Verify Java Version

```bash
java --version
# Expected: java 25.x.x
```

---

## Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/your-username/spring-boot-structure-task-scope-with-virtual-thread.git
cd spring-boot-structure-task-scope-with-virtual-thread

# 2. Build the project
./mvnw clean compile

# 3. Run the application
./mvnw spring-boot:run

# 4. Test the APIs
curl "http://localhost:8080/api/sequential?userId=U001"
curl "http://localhost:8080/api/structured/await-all?userId=U001"
```

### Quick Timing Test

```bash
# Sequential (~6s)
time curl -s "http://localhost:8080/api/sequential?userId=U001" > /dev/null

# Structured (~2s) — 3x faster!
time curl -s "http://localhost:8080/api/structured/await-all?userId=U001" > /dev/null
```

---

## Architecture

```
src/main/java/com/aamir/
├── SpringBootStructureTaskScopeWithVirtualThreadApplication.java
├── controller/
│   └── StructuredTaskScopeController.java    # 15 REST endpoints
├── service/
│   └── DummyService.java                     # Simulated I/O-bound services
└── exception/
    ├── ServiceCustomException.java           # Custom business exception
    ├── ErrorResponse.java                    # Structured error response record
    └── GlobalExceptionHandler.java           # @RestControllerAdvice handler

src/test/java/com/aamir/
└── StructuredTaskScopeControllerTest.java    # 16 integration tests
```

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     HTTP Client                             │
│                    (curl / browser)                          │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              GlobalExceptionHandler                         │
│         @RestControllerAdvice                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  ServiceCustomException    → ErrorResponse(503)     │    │
│  │  FailedException           → ErrorResponse(500)     │    │
│  │  Exception                 → ErrorResponse(500)     │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│           StructuredTaskScopeController                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ /sequential  │  │ /completable │  │ /structured  │      │
│  │              │  │   -future    │  │              │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      DummyService                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ getProfile  │  │ getOrder    │  │ alwaysFail  │         │
│  │ (2s sleep)  │  │ (2s sleep)  │  │ (1s + throw)│         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

---

## API Reference

### Success Scenarios

| # | Endpoint | Approach | Expected Time | Description |
|---|---|---|---|---|
| 1 | `GET /api/sequential` | Sequential | ~6s | Baseline — no concurrency |
| 2 | `GET /api/completable-future` | CompletableFuture | ~2s | Thread pool concurrency |
| 3 | `GET /api/completable-future/fail-fast` | CF Fail-Fast | ~1s | Manual cancellation on failure |
| 4 | `GET /api/completable-future/race` | CF Race | ~2s | `anyOf()` — first wins |
| 5 | `GET /api/structured/await-all` | `Joiner.awaitAll()` | ~2s | All results, never throws |
| 6 | `GET /api/structured/await-all-successful-or-throw` | `Joiner.awaitAllSuccessfulOrThrow()` | ~2s | All succeed or throw |
| 7 | `GET /api/structured/all-successful-or-throw` | `Joiner.allSuccessfulOrThrow()` | ~2s | Returns `Stream<Subtask<T>>` |
| 8 | `GET /api/structured/any-successful-result-or-throw` | `Joiner.anySuccessfulResultOrThrow()` | ~2s | Race — first success wins |
| 9 | `GET /api/structured/all-until` | `Joiner.allUntil(predicate)` | ~2s | Custom cancellation logic |

### Failure Scenarios

| # | Endpoint | Behavior | HTTP Status |
|---|---|---|---|
| 10 | `GET /api/failure/await-all` | Shows `FAILED` subtask state | 200 OK |
| 11 | `GET /api/failure/await-all-successful-or-throw` | Throws on first failure | 500 |
| 12 | `GET /api/failure/all-successful-or-throw` | Throws on first failure | 500 |
| 13 | `GET /api/failure/any-successful-result-or-throw` | All fail — throws | 500 |
| 14 | `GET /api/failure/all-until` | 2 failures trigger cancel | 200 OK |
| 15 | `GET /api/failure/any-with-fallback` | One fails, one succeeds | 200 OK |

### Sample Request/Response

**Request:**
```bash
curl -s "http://localhost:8080/api/structured/await-all?userId=U001"
```

**Response (200 OK):**
```json
{
  "api": "StructuredTaskScope + Joiner.awaitAll()",
  "profile": {
    "userId": "U001",
    "name": "John Doe",
    "email": "john@example.com"
  },
  "orders": {
    "userId": "U001",
    "orders": ["ORD-001", "ORD-002", "ORD-003"],
    "totalOrders": 3
  },
  "payments": {
    "userId": "U001",
    "balance": 1500.00,
    "currency": "USD"
  },
  "timeTakenMs": 2038,
  "timeTakenSeconds": "2.04",
  "expectedTimeSeconds": "~2.00 (concurrent with structured scope)",
  "note": "join() never throws - inspect each subtask manually"
}
```

**Error Response (500):**
```json
{
  "statusCode": 500,
  "error": "StructuredTaskScope Failure",
  "message": "Service unavailable for user: U001",
  "timestamp": "2026-09-05T11:50:50.370",
  "details": {
    "source": "structured-task-scope",
    "causeType": "ServiceCustomException"
  }
}
```

---

## Joiner Strategies Deep Dive

### Quick Reference

| # | Joiner | `scope.join()` returns | Throws on failure? | Cancellation |
|---|---|---|---|---|
| 1 | `awaitAll()` | `Void` | Never | None |
| 2 | `awaitAllSuccessfulOrThrow()` | `Void` | Yes (`FailedException`) | Siblings cancelled |
| 3 | `allSuccessfulOrThrow()` | `Stream<Subtask<T>>` | Yes (`FailedException`) | Siblings cancelled |
| 4 | `anySuccessfulResultOrThrow()` | `T` (first success) | Only if all fail | Losers cancelled |
| 5 | `allUntil(predicate)` | `Stream<Subtask<T>>` | Depends on predicate | Predicate-driven |

### Choosing the Right Joiner

```
Do you need all results?
├── YES, but tolerate partial failure
│   └── Use: Joiner.awaitAll()
│       → Check each Subtask.state() manually
│
├── YES, all must succeed
│   ├── Need individual results?
│   │   └── Use: Joiner.allSuccessfulOrThrow()
│   │       → scope.join() returns Stream<Subtask<T>>
│   │
│   └── Just need pass/fail?
│       └── Use: Joiner.awaitAllSuccessfulOrThrow()
│           → scope.join() returns Void
│
├── NO, just need the fastest one
│   └── Use: Joiner.anySuccessfulResultOrThrow()
│       → scope.join() returns T directly
│
└── Custom cancellation logic
    └── Use: Joiner.allUntil(predicate)
        → Write your own Predicate<Subtask>
```

### Detailed Examples

#### Joiner 1: `awaitAll()` — Best-Effort Aggregation

```java
try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
    var profile = scope.fork(() -> getProfile(userId));    // might fail
    var orders = scope.fork(() -> getOrders(userId));      // might fail
    var payments = scope.fork(() -> getPayments(userId));  // might fail

    scope.join();  // Never throws!

    // Manually check each subtask
    return Map.of(
        "profile", safeGet(profile, fallback),
        "orders", safeGet(orders, fallback),
        "payments", safeGet(payments, fallback)
    );
}
```

**When to use:** Dashboard data, best-effort enrichment, non-critical aggregations.

---

#### Joiner 2: `awaitAllSuccessfulOrThrow()` — All-or-Nothing Writes

```java
try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
    scope.fork(() -> orderRepo.save(order));
    scope.fork(() -> inventoryClient.reserve(order));
    scope.join();  // Throws if either write fails
}
```

**When to use:** Multi-step transactions, dual-write consistency.

---

#### Joiner 3: `allSuccessfulOrThrow()` — Results + Fail-Fast

```java
try (var scope = StructuredTaskScope.open(
        Joiner.<DeliveryTime>allSuccessfulOrThrow())) {
    supplierIds.forEach(id ->
        scope.fork(() -> supplierClient.getDeliveryTime(id)));
    return scope.join()
        .map(Subtask::get)
        .toList();
}
```

**When to use:** When you need all results AND fail-fast semantics.

---

#### Joiner 4: `anySuccessfulResultOrThrow()` — Redundant Providers

```java
try (var scope = StructuredTaskScope.open(
        Joiner.<Price>anySuccessfulResultOrThrow())) {
    scope.fork(() -> primaryFeed.getPrice(symbol));
    scope.fork(() -> backupFeed.getPrice(symbol));
    return scope.join();  // First success wins
}
```

**When to use:** Race conditions, fallback providers, latency optimization.

---

#### Joiner 5: `allUntil(predicate)` — Custom Cancellation

```java
var cancelAfterTwoFailures = new CancelAfterTwoFailures<T>();

try (var scope = StructuredTaskScope.open(
        Joiner.allUntil(cancelAfterTwoFailures))) {
    tasks.forEach(scope::fork);
    return scope.join().toList();
}

// Custom predicate
class CancelAfterTwoFailures<T> implements Predicate<Subtask<? extends T>> {
    private final AtomicInteger failed = new AtomicInteger();

    public boolean test(Subtask<? extends T> t) {
        return t.state() == Subtask.State.FAILED
            && failed.incrementAndGet() >= 2;
    }
}
```

**When to use:** Circuit breakers, N-of-M success patterns, custom resilience.

---

## Benefits & Use Cases

### Virtual Threads Benefits

| Benefit | Description | Impact |
|---|---|---|
| **Cheap to create** | A few hundred bytes each | Millions of threads possible |
| **Auto-unmount on I/O** | Blocking call parks the thread, frees carrier | No thread waste during I/O |
| **Same imperative style** | No reactive chains, no callbacks | Simpler code, easier debugging |
| **JVM-managed** | Scheduled onto carrier threads automatically | No thread pool tuning |

### StructuredTaskScope Benefits

| Benefit | Description | Impact |
|---|---|---|
| **No orphaned threads** | Siblings cancelled on failure | No resource leaks |
| **Composable Joiners** | Pluggable success/failure policies | Code reuse |
| **Stack traces** | Read like sequential code | Easy debugging |
| **Structured lifecycle** | Parent waits for all children | Predictable behavior |

### Use Cases Matrix

| Use Case | Recommended Joiner | Example |
|---|---|---|
| Aggregate data from multiple microservices | `allSuccessfulOrThrow()` | Dashboard aggregation |
| Best-effort data enrichment | `awaitAll()` | User profile + recommendations |
| Redundant data providers (race) | `anySuccessfulResultOrThrow()` | Primary + backup pricing |
| Multi-step transaction (all-or-nothing) | `awaitAllSuccessfulOrThrow()` | Order + inventory reservation |
| Custom resilience patterns | `allUntil(predicate)` | Circuit breaker, N-of-M |

### Before vs After Comparison

| Thread Pool + CompletableFuture | Virtual Threads + StructuredTaskScope |
|---|---|
| Fixed pool size caps concurrency | Concurrency scales with I/O, not thread count |
| Manual exception aggregation | Joiner encodes the success/failure policy |
| Easy to leak orphaned tasks | Sibling subtasks cancel automatically |
| Stack traces are hard to follow | Stack traces read like sequential code |
| `ExecutorService` + `shutdown()` | `try-with-resources` on `StructuredTaskScope` |
| `CompletableFuture.allOf()` — manual | `Joiner.awaitAll()` — built-in |

---

## Error Handling

### Exception Hierarchy

```
RuntimeException
└── ServiceCustomException
    ├── statusCode: int (503, 500, etc.)
    └── timestamp: String

StructuredTaskScope.FailedException
└── cause: Exception (the original failure)
```

### Error Response Format

```json
{
  "statusCode": 503,
  "error": "Service Unavailable",
  "message": "Service unavailable for user: U001",
  "timestamp": "2026-09-05T11:50:50.370",
  "details": {
    "source": "service-layer"
  }
}
```

### Global Exception Handler

| Exception | HTTP Status | Handler Method | Response |
|---|---|---|---|
| `ServiceCustomException` | Custom | `handleServiceCustomException` | Structured error with details |
| `FailedException` | 500 | `handleFailedException` | Structured with cause info |
| `Exception` | 500 | `handleGenericException` | Fallback error response |

---

## curl Test Commands

### Success Scenarios

```bash
# 1. Sequential (baseline — ~6s)
curl -s "http://localhost:8080/api/sequential?userId=U001" | python3 -m json.tool

# 2. CompletableFuture (~2s)
curl -s "http://localhost:8080/api/completable-future?userId=U001" | python3 -m json.tool

# 3. CompletableFuture Fail-Fast (returns 500 — manual cancellation)
curl -s -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/completable-future/fail-fast?userId=U001"

# 4. CompletableFuture Race (~2s — first wins)
curl -s "http://localhost:8080/api/completable-future/race?symbol=AAPL" | python3 -m json.tool

# 5. StructuredTaskScope + Joiner.awaitAll() (~2s)
curl -s "http://localhost:8080/api/structured/await-all?userId=U001" | python3 -m json.tool

# 6. StructuredTaskScope + Joiner.awaitAllSuccessfulOrThrow() (~2s)
curl -s "http://localhost:8080/api/structured/await-all-successful-or-throw?userId=U001" | python3 -m json.tool

# 7. StructuredTaskScope + Joiner.allSuccessfulOrThrow() (~2s)
curl -s "http://localhost:8080/api/structured/all-successful-or-throw?userId=U001" | python3 -m json.tool

# 8. StructuredTaskScope + Joiner.anySuccessfulResultOrThrow() (~2s)
curl -s "http://localhost:8080/api/structured/any-successful-result-or-throw?symbol=AAPL" | python3 -m json.tool

# 9. StructuredTaskScope + Joiner.allUntil(predicate) (~2s)
curl -s "http://localhost:8080/api/structured/all-until?userId=U001" | python3 -m json.tool
```

### Failure Scenarios

```bash
# 10. Failure + awaitAll() — shows FAILED subtask state (200 OK)
curl -s "http://localhost:8080/api/failure/await-all?userId=U001" | python3 -m json.tool

# 11. Failure + awaitAllSuccessfulOrThrow() — returns 500
curl -s -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/failure/await-all-successful-or-throw?userId=U001"

# 12. Failure + allSuccessfulOrThrow() — returns 500
curl -s -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/failure/all-successful-or-throw?userId=U001"

# 13. Failure + anySuccessfulResultOrThrow() — returns 500 (all fail)
curl -s -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/failure/any-successful-result-or-throw?userId=U001"

# 14. Failure + allUntil(predicate) — 2 failures trigger cancel
curl -s "http://localhost:8080/api/failure/all-until?userId=U001" | python3 -m json.tool

# 15. Failure + anySuccessfulResultOrThrow() with fallback — one fails, one succeeds
curl -s "http://localhost:8080/api/failure/any-with-fallback?userId=U001" | python3 -m json.tool
```

### Timing Comparison

```bash
# Quick timing test
echo "=== Sequential ===" && time curl -s "http://localhost:8080/api/sequential?userId=U001" > /dev/null
echo "=== Structured ===" && time curl -s "http://localhost:8080/api/structured/await-all?userId=U001" > /dev/null
```

---

## Java Features Used (8 → 25)

| Java Version | Feature | Where Used |
|---|---|---|
| **Java 8** | Lambdas | `scope.fork(() -> ...)` |
| **Java 8** | Stream API | `.map(Subtask::get).toList()` |
| **Java 8** | Functional Interface | `Predicate<Subtask<? extends T>>` |
| **Java 9** | `HttpClient` | Test class HTTP calls |
| **Java 9** | `Map.of()`, `List.of()` | DummyService return values |
| **Java 10** | `var` (local variable type inference) | All local variables in controller & tests |
| **Java 11** | `String.isBlank()`, `strip()` | String formatting |
| **Java 12** | `String.formatted()` | `"Price{...}".formatted(symbol)` |
| **Java 16** | `record` | `ErrorResponse` DTO |
| **Java 16** | `toList()` on Stream | `.map(Subtask::get).toList()` |
| **Java 17** | `switch` expressions | State handling |
| **Java 19** | Virtual Threads (Preview) | `spring.threads.virtual.enabled=true` |
| **Java 21** | StructuredTaskScope (Incubator) | `jdk.incubator.concurrent` |
| **Java 25** | StructuredTaskScope + Joiner (Preview) | `java.util.concurrent.StructuredTaskScope` |

---

## Configuration

### application.yaml

```yaml
spring:
  application:
    name: spring-boot-structure-task-scope-with-virtual-thread
  threads:
    virtual:
      enabled: true    # Enables virtual threads for Tomcat, @Async, @Scheduled
```

### pom.xml Key Settings

```xml
<properties>
    <java.version>25</java.version>
</properties>

<!-- Compiler: --enable-preview for StructuredTaskScope -->
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>25</release>
        <compilerArgs>
            <arg>--enable-preview</arg>
        </compilerArgs>
    </configuration>
</plugin>

<!-- Runtime: --enable-preview for JVM -->
<plugin>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <jvmArguments>--enable-preview</jvmArguments>
    </configuration>
</plugin>

<!-- Tests: --enable-preview -->
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>--enable-preview</argLine>
    </configuration>
</plugin>
```

---

## Best Practices

### Do's

| Practice | Why |
|---|---|
| Use `ReentrantLock` instead of `synchronized` | Avoids pinning the carrier thread |
| Use `ScopedValue` instead of `ThreadLocal` | Scoped values flow into forked subtasks |
| Create one virtual thread per task | Pooling defeats the purpose |
| Use `try-with-resources` on `StructuredTaskScope` | Ensures proper cleanup |
| Use `--enable-preview` flag | Required until API is finalized |

### Don'ts

| Anti-Pattern | Problem |
|---|---|
| `synchronized` blocks around blocking calls | Pins the carrier thread |
| `ThreadLocal` for per-request context | Doesn't flow into forked tasks |
| Pooling virtual threads | No benefit, adds overhead |
| Ignoring `InterruptedException` | Breaks cancellation |
| Catching `Throwable` in subtasks | Hides failures from Joiner |

### Performance Tips

```java
// GOOD: Use ReentrantLock
private final ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    // blocking operation
} finally {
    lock.unlock();
}

// BAD: synchronized (pins carrier thread)
synchronized (this) {
    // blocking operation — pins carrier thread!
}
```

---

## Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=StructuredTaskScopeControllerTest

# Run with output
./mvnw test -Dtest=StructuredTaskScopeControllerTest -Dsurefire.useFile=false

# Run and show coverage
./mvnw test jacoco:report
```

### Test Coverage

| Test | What it verifies |
|---|---|
| `sequential_shouldReturnDataWithTiming` | Sequential API returns correct data with ~6s timing |
| `completableFuture_shouldReturnDataWithTiming` | CompletableFuture API returns data with ~2s timing |
| `completableFutureFailFast_shouldReturnStructuredErrorResponse` | Fail-fast returns structured error with statusCode, error, message |
| `completableFutureRace_shouldReturnFastestResult` | CompletableFuture `anyOf()` races and returns first result |
| `structuredAwaitAll_shouldReturnDataWithTiming` | `Joiner.awaitAll()` returns all results, never throws |
| `structuredAwaitAllSuccessfulOrThrow_shouldReturnDataWithTiming` | `Joiner.awaitAllSuccessfulOrThrow()` succeeds when all OK |
| `structuredAllSuccessfulOrThrow_shouldReturnDataWithTiming` | `Joiner.allSuccessfulOrThrow()` returns stream of results |
| `structuredAnySuccessfulResultOrThrow_shouldReturnDataWithTiming` | `Joiner.anySuccessfulResultOrThrow()` races and returns first |
| `structuredAllUntil_shouldReturnDataWithTiming` | `Joiner.allUntil(predicate)` completes with custom logic |
| `sequential_shouldTakeMoreTimeThanStructured` | Sequential > Structured in timing |
| `failureAwaitAll_shouldShowFailedSubtaskState` | Failed subtask shows `FAILED` state |
| `failureAwaitAllSuccessfulOrThrow_shouldReturnStructuredErrorResponse` | Returns structured error with 500 status |
| `failureAllSuccessfulOrThrow_shouldReturnStructuredErrorResponse` | Returns structured error with 500 status |
| `failureAnySuccessfulResultOrThrow_shouldReturnStructuredErrorResponse` | Returns structured error with 500 status |
| `failureAllUntil_shouldTriggerCancelAfterTwoFailures` | Predicate triggers on 2 failures |
| `failureAnyWithFallback_shouldReturnSuccessFromWorkingTask` | One fails, one succeeds — fallback works |

---

## Troubleshooting

### Common Issues

| Issue | Solution |
|---|---|
| `ClassNotFoundException: StructuredTaskScope` | Add `--enable-preview` to compiler and runtime |
| `NoClassDefFoundError: StructuredTaskScope` | Ensure Java 25+ and `--enable-preview` flag |
| Virtual threads not working | Check `spring.threads.virtual.enabled=true` |
| Tests fail with `enable-preview` | Add `<argLine>--enable-preview</argLine>` to surefire plugin |
| `synchronized` blocks slow | Replace with `ReentrantLock` |

### Debugging Tips

```java
// Enable virtual thread debugging
System.setProperty("jdk.tracePinnedThreads", "short");

// Check thread type
Thread.currentThread().isVirtual();  // true for virtual threads
```

---

## FAQ

**Q: Is StructuredTaskScope production-ready?**
A: It's a preview API in Java 25. Use with caution in production, but the API is stable and expected to be finalized in Java 26+.

**Q: Can I use this with Spring Boot 3?**
A: No. StructuredTaskScope requires Java 25+, and Spring Boot 4 is the first version to support virtual threads natively.

**Q: How do I handle thread pinning?**
A: Replace `synchronized` blocks with `ReentrantLock` around blocking calls.

**Q: What's the difference between `awaitAll()` and `awaitAllSuccessfulOrThrow()`?**
A: `awaitAll()` never throws — you check each subtask manually. `awaitAllSuccessfulOrThrow()` throws `FailedException` if any subtask fails.

**Q: Can I mix CompletableFuture with StructuredTaskScope?**
A: Yes, but StructuredTaskScope is generally preferred for new code. CompletableFuture is useful for async streams or when you need `anyOf()`.

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Setup

```bash
# Clone your fork
git clone https://github.com/your-username/spring-boot-structure-task-scope-with-virtual-thread.git

# Build
./mvnw clean compile

# Run tests
./mvnw test

# Run locally
./mvnw spring-boot:run
```

---

## References

- [JEP 505: Structured Concurrency](https://openjdk.org/jeps/505)
- [JEP 462: Structured Concurrency (Incubator)](https://openjdk.org/jeps/462)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Spring Boot 4.1.1 Reference](https://docs.spring.io/spring-boot/4.1.1/reference/)
- [Virtual Threads Documentation](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html#ofVirtual())
- [StructuredTaskScope API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html)

---

## Author

**Mohammad Aamir**
- Senior Java Microservices Developer
- Specializing in Spring Boot, Microservices, and Cloud-Native Architecture

---

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- Spring Team for Spring Boot 4 virtual thread support
- OpenJDK team for StructuredTaskScope (JEP 505)
- The Java community for feedback on preview APIs

---

<p align="center">
  Made with care for the Java community
</p>
