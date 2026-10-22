package com.trading.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SIMULATION = """
        {
          "clients": [
            {"clientId":"BUYER","currencies":["SGD"],"positionCheck":false,"rating":1},
            {"clientId":"SELLER","currencies":["SGD"],"positionCheck":false,"rating":1}
          ],
          "instruments": [
            {"instrumentId":"SIA","currency":"SGD","lotSize":100}
          ],
          "orders": [
            {"time":"10:00:00","orderId":"S1","instrumentId":"SIA","quantity":100,"clientId":"SELLER","type":"LIMIT","price":32.10,"side":"SELL"},
            {"time":"10:00:01","orderId":"B1","instrumentId":"SIA","quantity":100,"clientId":"BUYER","type":"LIMIT","price":32.10,"side":"BUY"}
          ]
        }
        """;

    private SimulationServer.RunningServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = SimulationServer.create(0);
        server.start();
        client = HttpClient.newHttpClient();
        baseUrl = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.close();
    }

    @Test
    void reportsHealthAndOpenApiDocument() throws Exception {
        HttpResponse<String> health = get("/health");
        assertEquals(200, health.statusCode());
        assertEquals("ok", JSON.readTree(health.body()).get("status").asText());

        HttpResponse<String> missing = get("/health/extra");
        assertEquals(404, missing.statusCode());

        HttpResponse<String> openApi = get("/api/v1/openapi.json");
        assertEquals(200, openApi.statusCode());
        assertEquals("3.1.0", JSON.readTree(openApi.body()).get("openapi").asText());
    }

    @Test
    void runsAnIsolatedCrossingOrderSimulation() throws Exception {
        HttpResponse<String> response = post(SIMULATION, "application/json");
        assertEquals(200, response.statusCode());
        JsonNode result = JSON.readTree(response.body());
        assertEquals(1, result.get("executions").size());
        assertEquals(0, result.get("executions").get(0).get("price").decimalValue()
            .compareTo(new java.math.BigDecimal("32.10")));
        assertEquals(0, result.get("rejections").size());
        assertEquals(2, result.get("positions").size());

        // Reusing order IDs in a second request must not leak the first engine's state.
        HttpResponse<String> repeated = post(SIMULATION, "application/json; charset=utf-8");
        assertEquals(200, repeated.statusCode());
        assertEquals(result, JSON.readTree(repeated.body()));
    }

    @Test
    void reportsAggregatePositionsBeyondTheIntegerRangeWithoutOverflow() throws Exception {
        String body = SIMULATION.replace("\"rating\":1", "\"rating\":1,\"positions\":{\"SIA\":2147483647}");
        HttpResponse<String> response = post(body, "application/json");
        assertEquals(200, response.statusCode());
        JsonNode result = JSON.readTree(response.body());
        assertEquals(2_147_483_747L, result.at("/positions/0/netPosition").longValue());
        assertEquals(2_147_483_547L, result.at("/positions/1/netPosition").longValue());
    }

    @Test
    void returnsStructuredErrorsForInvalidRequests() throws Exception {
        HttpResponse<String> wrongType = post("{}", "text/plain");
        assertEquals(415, wrongType.statusCode());
        assertEquals(
            "UNSUPPORTED_MEDIA_TYPE",
            JSON.readTree(wrongType.body()).at("/error/code").asText()
        );

        HttpResponse<String> invalid = post("{}", "application/json");
        assertEquals(400, invalid.statusCode());
        assertTrue(JSON.readTree(invalid.body()).at("/error/message").asText().contains("client"));

        HttpResponse<String> unknownField = post(
            "{\"clients\":[],\"instruments\":[],\"orders\":[],\"unexpected\":true}",
            "application/json"
        );
        assertEquals(400, unknownField.statusCode());
        assertEquals("INVALID_JSON", JSON.readTree(unknownField.body()).at("/error/code").asText());
    }

    @Test
    void rejectsNullRequestsAndArrayElementsAsClientErrors() throws Exception {
        for (String body : List.of(
            "null",
            SIMULATION.replace("\"clients\": [", "\"clients\": [null,"),
            SIMULATION.replace("\"instruments\": [", "\"instruments\": [null,"),
            SIMULATION.replace("\"orders\": [", "\"orders\": [null,")
        )) {
            HttpResponse<String> response = post(body, "application/json");
            assertEquals(400, response.statusCode(), body);
            assertTrue(JSON.readTree(response.body()).at("/error/code").asText().startsWith("INVALID_"));
        }
    }

    @Test
    void rejectsAmbiguousOrCoercedJsonInsteadOfChangingTradingInputs() throws Exception {
        for (String body : List.of(
            SIMULATION.replace("\"quantity\":100", "\"quantity\":100.9"),
            SIMULATION.replace("\"quantity\":100", "\"quantity\":\"100\""),
            SIMULATION.replace("\"orderId\":\"B1\"", "\"orderId\":123"),
            SIMULATION.replace("\"positionCheck\":false", "\"positionCheck\":null"),
            SIMULATION.replace("\"positionCheck\":false,", ""),
            SIMULATION.replace("\"rating\":1", "\"rating\":1,\"rating\":2"),
            SIMULATION + " {}"
        )) {
            HttpResponse<String> response = post(body, "application/json");
            assertEquals(400, response.statusCode(), body);
            assertEquals("INVALID_JSON", JSON.readTree(response.body()).at("/error/code").asText());
        }
    }

    @Test
    void requiresExactJsonMediaTypeAndLimitsBodySize() throws Exception {
        HttpResponse<String> prefixedType = post(SIMULATION, "application/jsonp");
        assertEquals(415, prefixedType.statusCode());

        HttpResponse<String> oversized = post(" ".repeat(1_048_577), "application/json");
        assertEquals(413, oversized.statusCode());
        assertEquals("REQUEST_TOO_LARGE", JSON.readTree(oversized.body()).at("/error/code").asText());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
            HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> post(String body, String contentType) throws Exception {
        return client.send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/simulations"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }
}
