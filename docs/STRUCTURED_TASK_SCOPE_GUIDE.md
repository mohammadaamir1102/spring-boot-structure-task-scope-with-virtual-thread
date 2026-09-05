# Virtual Threads + StructuredTaskScope — Spring Boot 4 (Java 25)

A practical guide demonstrating **Virtual Threads** and **StructuredTaskScope** with all 5 built-in **Joiner** strategies in Spring Boot 4 microservices.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [API Reference](#api-reference)
  - [Success Scenarios](#success-scenarios)
  - [Failure Scenarios](#failure-scenarios)
- [Joiner Strategies Deep Dive](#joiner-strategies-deep-dive)
- [Benefits & Use Cases](#benefits--use-cases)
- [curl Test Commands](#curl-test-commands)
- [Java Features Used (8 → 25)](#java-features-used-8--25)
- [Configuration](#configuration)
- [Running Tests](#running-tests)

---

## Overview

This project demonstrates the evolution of concurrent programming in Java:

| Approach | Concurrency Model | Code Style | Scalability |
|---|---|---|---|
| **Sequential** | One thread, one call at a time | Imperative | Limited by thread count |
| **CompletableFuture** | Thread pool + async chains | Functional/Chained | Limited by pool size |
| **Virtual Threads + StructuredTaskScope** | JVM-managed, millions of threads | Imperative (blocking) | Scales with I/O, not threads |

### The Payoff (from PPT)

```
SEQUENTIAL — one thread, one call at a time
  Profile  5s | Orders  5s | Payments  5s  = 15s total

CONCURRENT — StructuredTaskScope forks all three, joins once
  Profile  5s | Orders  5s | Payments  5s  =  5s total  (3x faster)
```

---

## Prerequisites

- **Java 25** (with `--enable-preview` for StructuredTaskScope)
- **Spring Boot 4.1.1**
- **Maven 3.9+**

---

## Quick Start

```bash
# Clone and run
./mvnw spring-boot:run

# Application starts on http://localhost:8080
```

---

## Architecture

```
src/main/java/com/aamir/
├── SpringBootStructureTaskScopeWithVirtualThreadApplication.java
├── controller/
│   └── StructuredTaskScopeController.java    # All REST endpoints
└── service/
    └── DummyService.java                     # Simulated I/O-bound services

src/test/java/com/aamir/
└── StructuredTaskScopeControllerTest.java    # Integration tests
```

### DummyService Methods

| Method | Sleep | Behavior |
|---|---|---|
| `getProfileData(userId)` | 2s | Always succeeds |
| `getOrderData(userId)` | 2s | Always succeeds |
| `getPaymentData(userId)` | 2s | Always succeeds |
| `getPrimaryPrice(symbol)` | 2s | Always succeeds |
| `getBackupPrice(symbol)` | 3s | Always succeeds (slower) |
| `alwaysFail(userId)` | 1s | Always throws `RuntimeException` |

---

## API Reference

### Success Scenarios

#### 1. Sequential (No Concurrency)

> **Baseline** — calls services one after another. No concurrency.

```bash
curl "http://localhost:8080/api/sequential?userId=U001"
```

```json
{
  "api": "Sequential (No Concurrency)",
  "profile": { "userId": "U001", "name": "John Doe", "email": "john@example.com" },
  "orders": { "userId": "U001", "orders": ["ORD-001", "ORD-002", "ORD-003"], "totalOrders": 3 },
  "payments": { "userId": "U001", "balance": 1500.00, "currency": "USD" },
  "timeTakenMs": 6012,
  "timeTakenSeconds": "6.01",
  "expectedTimeSeconds": "6.00 (3 calls x 2s each)"
}
```

**Expected time: ~6 seconds** (3 calls × 2s each, sequential)

---

#### 2. CompletableFuture (Thread Pool)

> Pre-Java-21 approach — async chains with `CompletableFuture.supplyAsync()`.

```bash
curl "http://localhost:8080/api/completable-future?userId=U001"
```

```json
{
  "api": "CompletableFuture (Thread Pool)",
  "profile": { "userId": "U001", "name": "John Doe", "email": "john@example.com" },
  "orders": { "userId": "U001", "orders": ["ORD-001", "ORD-002", "ORD-003"], "totalOrders": 3 },
  "payments": { "userId": "U001", "balance": 1500.00, "currency": "USD" },
  "timeTakenMs": 2045,
  "timeTakenSeconds": "2.05",
  "expectedTimeSeconds": "~2.00 (concurrent with thread pool)"
}
```

**Expected time: ~2 seconds** (3 calls run concurrently via thread pool)

**Drawbacks:**
- Fixed pool size caps concurrency
- Manual exception aggregation
- Easy to leak orphaned tasks
- Stack traces are hard to follow

---

#### 3. StructuredTaskScope + Joiner.awaitAll()

> **Joiner 1/5** — Waits for every subtask. Never throws. Inspect each `Subtask.state()` manually.

```bash
curl "http://localhost:8080/api/structured/await-all?userId=U001"
```

```json
{
  "api": "StructuredTaskScope + Joiner.awaitAll()",
  "profile": { "userId": "U001", "name": "John Doe", "email": "john@example.com" },
  "orders": { "userId": "U001", "orders": ["ORD-001", "ORD-002", "ORD-003"], "totalOrders": 3 },
  "payments": { "userId": "U001", "balance": 1500.00, "currency": "USD" },
  "timeTakenMs": 2038,
  "timeTakenSeconds": "2.04",
  "expectedTimeSeconds": "~2.00 (concurrent with structured scope)",
  "note": "join() never throws - inspect each subtask manually"
}
```

**When to use:** Best for optional / best-effort data — one slow provider degrades gracefully instead of failing the whole call.

**Key characteristics:**
- `scope.join()` **never throws**
- You must check each `Subtask.state()` yourself
- All subtasks run to completion (no cancellation on failure)

---

#### 4. StructuredTaskScope + Joiner.awaitAllSuccessfulOrThrow()

> **Joiner 2/5** — Waits for all to succeed; throws `FailedException` on the first failure.

```bash
curl "http://localhost:8080/api/structured/await-all-successful-or-throw?userId=U001"
```

```json
{
  "api": "StructuredTaskScope + Joiner.awaitAllSuccessfulOrThrow()",
  "profile": { "userId": "U001", "name": "John Doe", "email": "john@example.com" },
  "orders": { "userId": "U001", "orders": ["ORD-001", "ORD-002", "ORD-003"], "totalOrders": 3 },
  "payments": { "userId": "U001", "balance": 1500.00, "currency": "USD" },
  "timeTakenMs": 2029,
  "timeTakenSeconds": "2.03",
  "expectedTimeSeconds": "~2.00 (concurrent, throws on first failure)",
  "note": "Throws FailedException if any subtask fails"
}
```

**When to use:** Best when you only need a pass/fail signal — both writes must succeed together, or the whole scope throws and neither counts.

**Key characteristics:**
- Throws `FailedException` if **any** subtask fails
- Cancels sibling subtasks on failure
- Results accessed via `Subtask.get()` after `scope.join()`

---

#### 5. StructuredTaskScope + Joiner.allSuccessfulOrThrow()

> **Joiner 3/5** — Same fail-fast policy, but `join()` returns `Stream<Subtask<T>>`.

```bash
curl "http://localhost:8080/api/structured/all-successful-or-throw?userId=U001"
```

```json
{
  "api": "StructuredTaskScope + Joiner.allSuccessfulOrThrow()",
  "results": [
    { "userId": "U001", "name": "John Doe", "email": "john@example.com" },
    { "userId": "U001", "orders": ["ORD-001", "ORD-002", "ORD-003"], "totalOrders": 3 },
    { "userId": "U001", "balance": 1500.00, "currency": "USD" }
  ],
  "timeTakenMs": 2035,
  "timeTakenSeconds": "2.04",
  "expectedTimeSeconds": "~2.00 (concurrent, returns Stream<Subtask<T>>)",
  "note": "Same fail-fast policy, but join() hands back Stream<Subtask<T>>"
}
```

**When to use:** Same as `awaitAllSuccessfulOrThrow()` but when you want all results as a stream — ideal when all subtasks share a return type.

**Key characteristics:**
- Throws on first failure
- `scope.join()` returns `Stream<Subtask<T>>`
- Use `.map(Subtask::get).toList()` to extract results

---

#### 6. StructuredTaskScope + Joiner.anySuccessfulResultOrThrow()

> **Joiner 4/5** — Races subtasks, returns the first success. Throws only if every subtask fails.

```bash
curl "http://localhost:8080/api/structured/any-successful-result-or-throw?symbol=AAPL"
```

```json
{
  "api": "StructuredTaskScope + Joiner.anySuccessfulResultOrThrow()",
  "price": "Price{symbol=AAPL, value=150.25, source=primary}",
  "timeTakenMs": 2032,
  "timeTakenSeconds": "2.03",
  "expectedTimeSeconds": "~2.00 (primary wins in 2s, backup cancelled)",
  "note": "Races subtasks - first success wins, losers cancelled automatically"
}
```

**When to use:** Best for redundant providers — race two data sources and take whichever answers first; losers are cancelled automatically.

**Key characteristics:**
- First success wins
- Remaining subtasks are **cancelled**
- Throws only if **all** subtasks fail
- `scope.join()` returns the result directly (not wrapped in `Subtask`)

---

#### 7. StructuredTaskScope + Joiner.allUntil(predicate)

> **Joiner 5/5** — Fully custom: keep collecting subtasks until your `Predicate` says "cancel now".

```bash
curl "http://localhost:8080/api/structured/all-until?userId=U001"
```

```json
{
  "api": "StructuredTaskScope + Joiner.allUntil(predicate)",
  "subtaskCount": 3,
  "timeTakenMs": 2041,
  "timeTakenSeconds": "2.04",
  "expectedTimeSeconds": "~2.00 (all succeed, predicate never triggers)",
  "note": "Full control - custom cancellation predicate"
}
```

**When to use:** Full control — write your own cancellation `Predicate`. E.g., cancel after 2 subtask failures, or after collecting N results.

**Key characteristics:**
- Custom `Predicate<Subtask<? extends T>>` controls cancellation
- `scope.join()` returns `Stream<Subtask<T>>`
- Predicate is evaluated on each completed subtask
- When predicate returns `true`, scope is cancelled

---

### Failure Scenarios

#### 8. Failure + Joiner.awaitAll()

> Shows how `awaitAll()` handles failures — `join()` never throws, you must inspect each subtask.

```bash
curl "http://localhost:8080/api/failure/await-all?userId=U001"
```

```json
{
  "api": "Failure Test + Joiner.awaitAll()",
  "successTask": { "userId": "U001", "name": "John Doe", "email": "john@example.com" },
  "failTask": { "error": "N/A" },
  "failTaskState": "FAILED",
  "timeTakenMs": 2035,
  "timeTakenSeconds": "2.04",
  "note": "join() never throws - you must check each subtask.state() manually"
}
```

**Key takeaway:** With `awaitAll()`, a failed subtask doesn't crash the scope. You get back all results and must check `Subtask.State` yourself.

---

#### 9-11. Failure + Throws Joiners (return 500)

These endpoints demonstrate that **throwing Joiners cause the scope to throw `FailedException`** when any subtask fails:

```bash
# These return HTTP 500 (StructuredTaskScope$FailedException)

curl "http://localhost:8080/api/failure/await-all-successful-or-throw?userId=U001"
curl "http://localhost:8080/api/failure/all-successful-or-throw?userId=U001"
curl "http://localhost:8080/api/failure/any-successful-result-or-throw?userId=U001"
```

**Key takeaway:** When using `awaitAllSuccessfulOrThrow()`, `allSuccessfulOrThrow()`, or `anySuccessfulResultOrThrow()`, any subtask failure causes the scope to throw immediately.

---

#### 12. Failure + Joiner.allUntil(predicate)

> Demonstrates custom cancellation — after 2 failures, the predicate triggers and cancels the scope.

```bash
curl "http://localhost:8080/api/failure/all-until?userId=U001"
```

```json
{
  "api": "Failure Test + Joiner.allUntil(predicate)",
  "subtaskCount": 3,
  "timeTakenMs": 2045,
  "timeTakenSeconds": "2.05",
  "note": "2 failures trigger cancel - remaining subtasks cancelled, scope returns early"
}
```

**Custom Predicate Implementation:**

```java
static class CancelAfterTwoFailures<T> implements Predicate<Subtask<? extends T>> {
    private final AtomicInteger failed = new AtomicInteger();

    @Override
    public boolean test(Subtask<? extends T> t) {
        return t.state() == Subtask.State.FAILED
                && failed.incrementAndGet() >= 2;
    }
}
```

---

#### 13. Failure + Joiner.anySuccessfulResultOrThrow() with Fallback

> One task fails, one succeeds — the first success wins, the failed task is cancelled.

```bash
curl "http://localhost:8080/api/failure/any-with-fallback?userId=U001"
```

```json
{
  "api": "Failure Test + Joiner.anySuccessfulResultOrThrow() with fallback",
  "price": "Price{symbol=U001, value=150.25, source=primary}",
  "timeTakenMs": 2035,
  "timeTakenSeconds": "2.04",
  "note": "One fails, one succeeds - first success wins, failed task cancelled"
}
```

---

## Joiner Strategies Deep Dive

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

---

## Benefits & Use Cases

### Virtual Threads Benefits

| Benefit | Description |
|---|---|
| **Cheap to create** | A few hundred bytes each — spin up millions without exhausting memory |
| **Auto-unmount on I/O** | Blocking call parks the virtual thread and instantly frees its carrier |
| **Same imperative style** | No `CompletableFuture` chains, no reactive operators — plain blocking code, at scale |
| **JVM-managed** | Scheduled by the JVM onto a small pool of carrier (platform) threads |

### StructuredTaskScope Benefits

| Benefit | Description |
|---|---|
| **No orphaned threads** | If one subtask fails, siblings are cancelled automatically — nothing leaks |
| **Composable Joiners** | Pluggable Joiner strategies — composition over inheritance |
| **Stack traces read like sequential code** | No async stack trace debugging |
| **Structured lifecycle** | Parent never proceeds until every child finishes or is cancelled |

### Use Cases

| Use Case | Recommended Joiner |
|---|---|
| **Aggregate data from multiple microservices** | `allSuccessfulOrThrow()` — get all results, fail fast |
| **Best-effort data enrichment** | `awaitAll()` — degrade gracefully if one service is slow |
| **Redundant data providers (race)** | `anySuccessfulResultOrThrow()` — take the fastest response |
| **Multi-step transaction (all-or-nothing)** | `awaitAllSuccessfulOrThrow()` — both writes must succeed |
| **Custom resilience patterns** | `allUntil(predicate)` — e.g., cancel after N failures |

### Before vs After

| Thread Pool + CompletableFuture | Virtual Threads + StructuredTaskScope |
|---|---|
| Fixed pool size caps concurrency | Concurrency scales with I/O, not thread count |
| Manual exception aggregation | Joiner encodes the success/failure policy |
| Easy to leak orphaned tasks | Sibling subtasks cancel automatically |
| Stack traces are hard to follow | Stack traces read like sequential code |

---

## curl Test Commands

### Success Scenarios

```bash
# 1. Sequential (baseline — ~6s)
curl -s "http://localhost:8080/api/sequential?userId=U001" | python3 -m json.tool

# 2. CompletableFuture (~2s)
curl -s "http://localhost:8080/api/completable-future?userId=U001" | python3 -m json.tool

# 3. StructuredTaskScope + Joiner.awaitAll() (~2s)
curl -s "http://localhost:8080/api/structured/await-all?userId=U001" | python3 -m json.tool

# 4. StructuredTaskScope + Joiner.awaitAllSuccessfulOrThrow() (~2s)
curl -s "http://localhost:8080/api/structured/await-all-successful-or-throw?userId=U001" | python3 -m json.tool

# 5. StructuredTaskScope + Joiner.allSuccessfulOrThrow() (~2s)
curl -s "http://localhost:8080/api/structured/all-successful-or-throw?userId=U001" | python3 -m json.tool

# 6. StructuredTaskScope + Joiner.anySuccessfulResultOrThrow() (~2s)
curl -s "http://localhost:8080/api/structured/any-successful-result-or-throw?symbol=AAPL" | python3 -m json.tool

# 7. StructuredTaskScope + Joiner.allUntil(predicate) (~2s)
curl -s "http://localhost:8080/api/structured/all-until?userId=U001" | python3 -m json.tool
```

### Failure Scenarios

```bash
# 8. Failure + awaitAll() — shows FAILED subtask state (200 OK)
curl -s "http://localhost:8080/api/failure/await-all?userId=U001" | python3 -m json.tool

# 9. Failure + awaitAllSuccessfulOrThrow() — returns 500
curl -s -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/failure/await-all-successful-or-throw?userId=U001"

# 10. Failure + allSuccessfulOrThrow() — returns 500
curl -s -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/failure/all-successful-or-throw?userId=U001"

# 11. Failure + anySuccessfulResultOrThrow() — returns 500 (all fail)
curl -s -w "\nHTTP Status: %{http_code}\n" "http://localhost:8080/api/failure/any-successful-result-or-throw?userId=U001"

# 12. Failure + allUntil(predicate) — 2 failures trigger cancel
curl -s "http://localhost:8080/api/failure/all-until?userId=U001" | python3 -m json.tool

# 13. Failure + anySuccessfulResultOrThrow() with fallback — one fails, one succeeds
curl -s "http://localhost:8080/api/failure/any-with-fallback?userId=U001" | python3 -m json.tool
```

### Timing Comparison

```bash
# Run sequential and compare timeTakenMs (~6000ms)
time curl -s "http://localhost:8080/api/sequential?userId=U001" > /dev/null

# Run structured and compare timeTakenMs (~2000ms)
time curl -s "http://localhost:8080/api/structured/await-all?userId=U001" > /dev/null
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
| **Java 12** | `String.format()` → `formatted()` | `"Price{...}".formatted(symbol)` |
| **Java 16** | `record` (if needed) | Response DTOs |
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

### Best Practices (from PPT)

| Warning | Recommendation |
|---|---|
| `synchronized` blocks around blocking calls still pin the carrier thread | Prefer `ReentrantLock` |
| Don't use `ThreadLocal` for per-request context | Use `ScopedValue` |
| Don't pool virtual threads | Create one per task — pooling defeats their purpose |
| Preview API | Run with `--enable-preview --release 25` until finalized |

---

## Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=StructuredTaskScopeControllerTest

# Run with output
./mvnw test -Dtest=StructuredTaskScopeControllerTest -Dsurefire.useFile=false
```

### Test Coverage

| Test | What it verifies |
|---|---|
| `sequential_shouldReturnDataWithTiming` | Sequential API returns correct data with ~6s timing |
| `completableFuture_shouldReturnDataWithTiming` | CompletableFuture API returns data with ~2s timing |
| `structuredAwaitAll_shouldReturnDataWithTiming` | `Joiner.awaitAll()` returns all results, never throws |
| `structuredAwaitAllSuccessfulOrThrow_shouldReturnDataWithTiming` | `Joiner.awaitAllSuccessfulOrThrow()` succeeds when all OK |
| `structuredAllSuccessfulOrThrow_shouldReturnDataWithTiming` | `Joiner.allSuccessfulOrThrow()` returns stream of results |
| `structuredAnySuccessfulResultOrThrow_shouldReturnDataWithTiming` | `Joiner.anySuccessfulResultOrThrow()` races and returns first |
| `structuredAllUntil_shouldReturnDataWithTiming` | `Joiner.allUntil(predicate)` completes with custom logic |
| `sequential_shouldTakeMoreTimeThanStructured` | Sequential > Structured in timing |
| `failureAwaitAll_shouldShowFailedSubtaskState` | Failed subtask shows `FAILED` state |
| `failureAwaitAllSuccessfulOrThrow_shouldReturn500` | Failure causes HTTP 500 |
| `failureAllSuccessfulOrThrow_shouldReturn500` | Failure causes HTTP 500 |
| `failureAnySuccessfulResultOrThrow_shouldReturn500` | All-fail causes HTTP 500 |
| `failureAllUntil_shouldTriggerCancelAfterTwoFailures` | Predicate triggers on 2 failures |
| `failureAnyWithFallback_shouldReturnSuccessFromWorkingTask` | One fails, one succeeds — fallback works |
