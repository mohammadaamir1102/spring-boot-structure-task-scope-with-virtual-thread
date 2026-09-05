package com.aamir;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StructuredTaskScopeControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
    }

    private Map<String, Object> get(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), Map.class);
    }

    private int getStatusCode(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:%d%s".formatted(port, path)))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    @Test
    void sequential_shouldReturnDataWithTiming() throws Exception {
        var body = get("/api/sequential?userId=U001");

        assertThat(body.get("api")).isEqualTo("Sequential (No Concurrency)");
        assertThat(body.get("profile")).isNotNull();
        assertThat(body.get("orders")).isNotNull();
        assertThat(body.get("payments")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("timeTakenSeconds")).isNotNull();
        assertThat(body.get("expectedTimeSeconds")).isEqualTo("6.00 (3 calls x 2s each)");
    }

    @Test
    void completableFuture_shouldReturnDataWithTiming() throws Exception {
        var body = get("/api/completable-future?userId=U001");

        assertThat(body.get("api")).isEqualTo("CompletableFuture (Thread Pool)");
        assertThat(body.get("profile")).isNotNull();
        assertThat(body.get("orders")).isNotNull();
        assertThat(body.get("payments")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("timeTakenSeconds")).isNotNull();
    }

    @Test
    void completableFutureFailFast_shouldReturn500() throws Exception {
        var statusCode = getStatusCode("/api/completable-future/fail-fast?userId=U001");
        assertThat(statusCode).isEqualTo(500);
    }

    @Test
    void completableFutureRace_shouldReturnFastestResult() throws Exception {
        var body = get("/api/completable-future/race?symbol=AAPL");

        assertThat(body.get("api")).isEqualTo("CompletableFuture Race (anyOf)");
        assertThat(body.get("price")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("timeTakenSeconds")).isNotNull();
        assertThat(body.get("note")).isEqualTo("Manual cancellation of loser required - no built-in cancellation policy");
    }

    @Test
    void structuredAwaitAll_shouldReturnDataWithTiming() throws Exception {
        var body = get("/api/structured/await-all?userId=U001");

        assertThat(body.get("api")).isEqualTo("StructuredTaskScope + Joiner.awaitAll()");
        assertThat(body.get("profile")).isNotNull();
        assertThat(body.get("orders")).isNotNull();
        assertThat(body.get("payments")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("note")).isEqualTo("join() never throws - inspect each subtask manually");
    }

    @Test
    void structuredAwaitAllSuccessfulOrThrow_shouldReturnDataWithTiming() throws Exception {
        var body = get("/api/structured/await-all-successful-or-throw?userId=U001");

        assertThat(body.get("api")).isEqualTo("StructuredTaskScope + Joiner.awaitAllSuccessfulOrThrow()");
        assertThat(body.get("profile")).isNotNull();
        assertThat(body.get("orders")).isNotNull();
        assertThat(body.get("payments")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("note")).isEqualTo("Throws FailedException if any subtask fails");
    }

    @Test
    void structuredAllSuccessfulOrThrow_shouldReturnDataWithTiming() throws Exception {
        var body = get("/api/structured/all-successful-or-throw?userId=U001");

        assertThat(body.get("api")).isEqualTo("StructuredTaskScope + Joiner.allSuccessfulOrThrow()");
        assertThat(body.get("results")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("note")).isEqualTo("Same fail-fast policy, but join() hands back Stream<Subtask<T>>");
    }

    @Test
    void structuredAnySuccessfulResultOrThrow_shouldReturnDataWithTiming() throws Exception {
        var body = get("/api/structured/any-successful-result-or-throw?symbol=AAPL");

        assertThat(body.get("api")).isEqualTo("StructuredTaskScope + Joiner.anySuccessfulResultOrThrow()");
        assertThat(body.get("price")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("note")).isEqualTo("Races subtasks - first success wins, losers cancelled automatically");
    }

    @Test
    void structuredAllUntil_shouldReturnDataWithTiming() throws Exception {
        var body = get("/api/structured/all-until?userId=U001");

        assertThat(body.get("api")).isEqualTo("StructuredTaskScope + Joiner.allUntil(predicate)");
        assertThat(body.get("subtaskCount")).isEqualTo(3);
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("note")).isEqualTo("Full control - custom cancellation predicate");
    }

    @Test
    void sequential_shouldTakeMoreTimeThanStructured() throws Exception {
        var sequentialBody = get("/api/sequential?userId=U001");
        var structuredBody = get("/api/structured/await-all?userId=U001");

        var sequentialTime = ((Number) sequentialBody.get("timeTakenMs")).longValue();
        var structuredTime = ((Number) structuredBody.get("timeTakenMs")).longValue();

        assertThat(sequentialTime)
                .as("Sequential (%dms) should take more time than Structured (%dms)", sequentialTime, structuredTime)
                .isGreaterThan(structuredTime);
    }

    @Test
    void failureAwaitAll_shouldShowFailedSubtaskState() throws Exception {
        var body = get("/api/failure/await-all?userId=U001");

        assertThat(body.get("api")).isEqualTo("Failure Test + Joiner.awaitAll()");
        assertThat(body.get("successTask")).isNotNull();
        assertThat(body.get("failTask")).isNotNull();
        assertThat(body.get("failTaskState")).isEqualTo("FAILED");
        assertThat(body.get("note")).isEqualTo("join() never throws - you must check each subtask.state() manually");
    }

    @Test
    void failureAwaitAllSuccessfulOrThrow_shouldReturn500() throws Exception {
        var statusCode = getStatusCode("/api/failure/await-all-successful-or-throw?userId=U001");
        assertThat(statusCode).isEqualTo(500);
    }

    @Test
    void failureAllSuccessfulOrThrow_shouldReturn500() throws Exception {
        var statusCode = getStatusCode("/api/failure/all-successful-or-throw?userId=U001");
        assertThat(statusCode).isEqualTo(500);
    }

    @Test
    void failureAnySuccessfulResultOrThrow_shouldReturn500() throws Exception {
        var statusCode = getStatusCode("/api/failure/any-successful-result-or-throw?userId=U001");
        assertThat(statusCode).isEqualTo(500);
    }

    @Test
    void failureAllUntil_shouldTriggerCancelAfterTwoFailures() throws Exception {
        var body = get("/api/failure/all-until?userId=U001");

        assertThat(body.get("api")).isEqualTo("Failure Test + Joiner.allUntil(predicate)");
        assertThat(body.get("subtaskCount")).isNotNull();
        assertThat(body.get("timeTakenMs")).isNotNull();
        assertThat(body.get("note")).isEqualTo("2 failures trigger cancel - remaining subtasks cancelled, scope returns early");
    }

    @Test
    void failureAnyWithFallback_shouldReturnSuccessFromWorkingTask() throws Exception {
        var body = get("/api/failure/any-with-fallback?userId=U001");

        assertThat(body.get("api")).isEqualTo("Failure Test + Joiner.anySuccessfulResultOrThrow() with fallback");
        assertThat(body.get("price")).isNotNull();
        assertThat(body.get("note")).isEqualTo("One fails, one succeeds - first success wins, failed task cancelled");
    }
}
