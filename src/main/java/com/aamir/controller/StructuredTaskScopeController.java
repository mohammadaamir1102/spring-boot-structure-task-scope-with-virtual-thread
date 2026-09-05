package com.aamir.controller;

import com.aamir.service.DummyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@RestController
@RequestMapping("/api")
public class StructuredTaskScopeController {

    private final DummyService dummyService;

    public StructuredTaskScopeController(DummyService dummyService) {
        this.dummyService = dummyService;
    }

    @GetMapping("/sequential")
    public Map<String, Object> sequential(@RequestParam(defaultValue = "U001") String userId) {

        var start = System.currentTimeMillis();

        var profile = dummyService.getProfileData(userId);
        var orders = dummyService.getOrderData(userId);
        var payments = dummyService.getPaymentData(userId);

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "Sequential (No Concurrency)");
        result.put("profile", profile);
        result.put("orders", orders);
        result.put("payments", payments);
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("expectedTimeSeconds", "6.00 (3 calls x 2s each)");
        return result;
    }

    @GetMapping("/completable-future")
    public Map<String, Object> completableFuture(@RequestParam(defaultValue = "U001") String userId)
            throws Exception {

        var start = System.currentTimeMillis();

        var profileFuture = CompletableFuture.supplyAsync(() -> dummyService.getProfileData(userId));
        var ordersFuture = CompletableFuture.supplyAsync(() -> dummyService.getOrderData(userId));
        var paymentsFuture = CompletableFuture.supplyAsync(() -> dummyService.getPaymentData(userId));

        CompletableFuture.allOf(profileFuture, ordersFuture, paymentsFuture).join();

        var profile = profileFuture.get();
        var orders = ordersFuture.get();
        var payments = paymentsFuture.get();

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "CompletableFuture (Thread Pool)");
        result.put("profile", profile);
        result.put("orders", orders);
        result.put("payments", payments);
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("expectedTimeSeconds", "~2.00 (concurrent with thread pool)");
        return result;
    }

    @GetMapping("/structured/await-all")
    public Map<String, Object> structuredAwaitAll(@RequestParam(defaultValue = "U001") String userId)
            throws InterruptedException {

        var start = System.currentTimeMillis();

        Subtask<Map<String, Object>> profileTask;
        Subtask<Map<String, Object>> ordersTask;
        Subtask<Map<String, Object>> paymentsTask;

        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            profileTask = scope.fork(() -> dummyService.getProfileData(userId));
            ordersTask = scope.fork(() -> dummyService.getOrderData(userId));
            paymentsTask = scope.fork(() -> dummyService.getPaymentData(userId));

            scope.join();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "StructuredTaskScope + Joiner.awaitAll()");
        result.put("profile", safeGet(profileTask, Map.of("error", "N/A")));
        result.put("orders", safeGet(ordersTask, Map.of("error", "N/A")));
        result.put("payments", safeGet(paymentsTask, Map.of("error", "N/A")));
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("expectedTimeSeconds", "~2.00 (concurrent with structured scope)");
        result.put("note", "join() never throws - inspect each subtask manually");
        return result;
    }

    @GetMapping("/structured/await-all-successful-or-throw")
    public Map<String, Object> structuredAwaitAllSuccessfulOrThrow(
            @RequestParam(defaultValue = "U001") String userId) throws InterruptedException {

        var start = System.currentTimeMillis();

        Subtask<Map<String, Object>> profileTask;
        Subtask<Map<String, Object>> ordersTask;
        Subtask<Map<String, Object>> paymentsTask;

        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
            profileTask = scope.fork(() -> dummyService.getProfileData(userId));
            ordersTask = scope.fork(() -> dummyService.getOrderData(userId));
            paymentsTask = scope.fork(() -> dummyService.getPaymentData(userId));

            scope.join();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "StructuredTaskScope + Joiner.awaitAllSuccessfulOrThrow()");
        result.put("profile", profileTask.get());
        result.put("orders", ordersTask.get());
        result.put("payments", paymentsTask.get());
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("expectedTimeSeconds", "~2.00 (concurrent, throws on first failure)");
        result.put("note", "Throws FailedException if any subtask fails");
        return result;
    }

    @GetMapping("/structured/all-successful-or-throw")
    public Map<String, Object> structuredAllSuccessfulOrThrow(
            @RequestParam(defaultValue = "U001") String userId) throws InterruptedException {

        var start = System.currentTimeMillis();

        List<Map<String, Object>> subtaskResults;

        try (var scope = StructuredTaskScope.open(
                Joiner.<Map<String, Object>>allSuccessfulOrThrow())) {
            scope.fork(() -> dummyService.getProfileData(userId));
            scope.fork(() -> dummyService.getOrderData(userId));
            scope.fork(() -> dummyService.getPaymentData(userId));

            subtaskResults = scope.join()
                    .map(Subtask::get)
                    .toList();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "StructuredTaskScope + Joiner.allSuccessfulOrThrow()");
        result.put("results", subtaskResults);
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("expectedTimeSeconds", "~2.00 (concurrent, returns Stream<Subtask<T>>)");
        result.put("note", "Same fail-fast policy, but join() hands back Stream<Subtask<T>>");
        return result;
    }

    @GetMapping("/structured/any-successful-result-or-throw")
    public Map<String, Object> structuredAnySuccessfulResultOrThrow(
            @RequestParam(defaultValue = "AAPL") String symbol) throws InterruptedException {

        var start = System.currentTimeMillis();

        String priceResult;

        try (var scope = StructuredTaskScope.open(
                Joiner.<String>anySuccessfulResultOrThrow())) {
            scope.fork(() -> dummyService.getPrimaryPrice(symbol));
            scope.fork(() -> dummyService.getBackupPrice(symbol));

            priceResult = scope.join();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "StructuredTaskScope + Joiner.anySuccessfulResultOrThrow()");
        result.put("price", priceResult);
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("expectedTimeSeconds", "~2.00 (primary wins in 2s, backup cancelled)");
        result.put("note", "Races subtasks - first success wins, losers cancelled automatically");
        return result;
    }

    @GetMapping("/structured/all-until")
    public Map<String, Object> structuredAllUntil(@RequestParam(defaultValue = "U001") String userId)
            throws InterruptedException {

        var start = System.currentTimeMillis();

        List<Subtask<Map<String, Object>>> subtaskResults;

        try (var scope = StructuredTaskScope.open(
                Joiner.allUntil(new CancelAfterTwoFailures<Map<String, Object>>()))) {
            scope.fork(() -> dummyService.getProfileData(userId));
            scope.fork(() -> dummyService.getOrderData(userId));
            scope.fork(() -> dummyService.getPaymentData(userId));

            subtaskResults = scope.join().toList();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "StructuredTaskScope + Joiner.allUntil(predicate)");
        result.put("subtaskCount", subtaskResults.size());
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("expectedTimeSeconds", "~2.00 (all succeed, predicate never triggers)");
        result.put("note", "Full control - custom cancellation predicate");
        return result;
    }

    @GetMapping("/failure/await-all")
    public Map<String, Object> failureAwaitAll(@RequestParam(defaultValue = "U001") String userId)
            throws InterruptedException {

        var start = System.currentTimeMillis();

        Subtask<Map<String, Object>> successTask;
        Subtask<Map<String, Object>> failTask;

        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            successTask = scope.fork(() -> dummyService.getProfileData(userId));
            failTask = scope.fork(() -> dummyService.alwaysFail(userId));

            scope.join();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "Failure Test + Joiner.awaitAll()");
        result.put("successTask", safeGet(successTask, Map.of("error", "N/A")));
        result.put("failTask", safeGet(failTask, Map.of("error", "N/A")));
        result.put("failTaskState", failTask.state().toString());
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("note", "join() never throws - you must check each subtask.state() manually");
        return result;
    }

    @GetMapping("/failure/await-all-successful-or-throw")
    public Map<String, Object> failureAwaitAllSuccessfulOrThrow(
            @RequestParam(defaultValue = "U001") String userId) throws InterruptedException {

        var start = System.currentTimeMillis();

        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
            scope.fork(() -> dummyService.getProfileData(userId));
            scope.fork(() -> dummyService.alwaysFail(userId));

            scope.join();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "Failure Test + Joiner.awaitAllSuccessfulOrThrow()");
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("note", "This should never return - scope.join() throws FailedException");
        return result;
    }

    @GetMapping("/failure/all-successful-or-throw")
    public Map<String, Object> failureAllSuccessfulOrThrow(
            @RequestParam(defaultValue = "U001") String userId) throws InterruptedException {

        var start = System.currentTimeMillis();

        try (var scope = StructuredTaskScope.open(
                Joiner.<Map<String, Object>>allSuccessfulOrThrow())) {
            scope.fork(() -> dummyService.getProfileData(userId));
            scope.fork(() -> dummyService.alwaysFail(userId));

            scope.join()
                    .map(Subtask::get)
                    .toList();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "Failure Test + Joiner.allSuccessfulOrThrow()");
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("note", "This should never return - scope.join() throws FailedException");
        return result;
    }

    @GetMapping("/failure/any-successful-result-or-throw")
    public Map<String, Object> failureAnySuccessfulResultOrThrow(
            @RequestParam(defaultValue = "U001") String userId) throws InterruptedException {

        var start = System.currentTimeMillis();

        String priceResult;

        try (var scope = StructuredTaskScope.open(
                Joiner.<String>anySuccessfulResultOrThrow())) {
            scope.fork(() -> dummyService.alwaysFail(userId));
            scope.fork(() -> dummyService.alwaysFail(userId));

            priceResult = scope.join();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "Failure Test + Joiner.anySuccessfulResultOrThrow()");
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("note", "This should never return - all subtasks fail, scope.join() throws");
        return result;
    }

    @GetMapping("/failure/all-until")
    public Map<String, Object> failureAllUntil(@RequestParam(defaultValue = "U001") String userId)
            throws InterruptedException {

        var start = System.currentTimeMillis();

        List<Subtask<Map<String, Object>>> subtaskResults;

        try (var scope = StructuredTaskScope.open(
                Joiner.allUntil(new CancelAfterTwoFailures<Map<String, Object>>()))) {
            scope.fork(() -> dummyService.alwaysFail(userId));
            scope.fork(() -> dummyService.alwaysFail(userId));
            scope.fork(() -> dummyService.getProfileData(userId));

            subtaskResults = scope.join().toList();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "Failure Test + Joiner.allUntil(predicate)");
        result.put("subtaskCount", subtaskResults.size());
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("note", "2 failures trigger cancel - remaining subtasks cancelled, scope returns early");
        return result;
    }

    @GetMapping("/failure/any-with-fallback")
    public Map<String, Object> failureAnyWithFallback(
            @RequestParam(defaultValue = "U001") String userId) throws InterruptedException {

        var start = System.currentTimeMillis();

        String priceResult;

        try (var scope = StructuredTaskScope.open(
                Joiner.<String>anySuccessfulResultOrThrow())) {
            scope.fork(() -> dummyService.alwaysFail(userId));
            scope.fork(() -> dummyService.getPrimaryPrice(userId));

            priceResult = scope.join();
        }

        var end = System.currentTimeMillis();
        var timeTakenMs = end - start;

        var result = new LinkedHashMap<String, Object>();
        result.put("api", "Failure Test + Joiner.anySuccessfulResultOrThrow() with fallback");
        result.put("price", priceResult);
        result.put("timeTakenMs", timeTakenMs);
        result.put("timeTakenSeconds", "%.2f".formatted(timeTakenMs / 1000.0));
        result.put("note", "One fails, one succeeds - first success wins, failed task cancelled");
        return result;
    }

    private <T> T safeGet(Subtask<T> subtask, T fallback) {
        return subtask.state() == Subtask.State.SUCCESS ? subtask.get() : fallback;
    }

    static class CancelAfterTwoFailures<T> implements Predicate<Subtask<? extends T>> {
        private final AtomicInteger failed = new AtomicInteger();

        @Override
        public boolean test(Subtask<? extends T> t) {
            return t.state() == Subtask.State.FAILED
                    && failed.incrementAndGet() >= 2;
        }
    }
}
