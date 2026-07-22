package com.trading.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.trading.engine.OrderMatchingEngine;
import com.trading.model.Client;
import com.trading.model.Instrument;
import com.trading.model.Order;
import com.trading.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** Lightweight HTTP adapter for isolated, batch-based matching simulations. */
public final class SimulationServer {
    private static final Logger logger = LoggerFactory.getLogger(SimulationServer.class);
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private SimulationServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        RunningServer server = create(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.printf("LimitForge simulation API listening on http://localhost:%d%n", port);
    }

    static RunningServer create(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", exact("GET", SimulationServer::health));
        server.createContext("/api/v1/openapi.json", exact("GET", SimulationServer::openApi));
        server.createContext("/api/v1/simulations", exact("POST", SimulationServer::simulate));
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()))
        );
        server.setExecutor(executor);
        return new RunningServer(server, executor);
    }

    private static HttpHandler exact(String method, ExchangeAction action) {
        return exchange -> {
            try {
                if (!exchange.getRequestURI().getPath().equals(exchange.getHttpContext().getPath())) {
                    error(exchange, 404, "NOT_FOUND", "Route not found");
                    return;
                }
                if (!exchange.getRequestMethod().equals(method)) {
                    exchange.getResponseHeaders().set("Allow", method);
                    error(exchange, 405, "METHOD_NOT_ALLOWED", "Expected " + method);
                    return;
                }
                action.handle(exchange);
            } catch (RequestException e) {
                error(exchange, e.status(), e.code(), e.getMessage());
            } catch (Exception e) {
                logger.error("Unhandled simulation API error", e);
                error(exchange, 500, "INTERNAL_ERROR", "Simulation failed");
            } finally {
                exchange.close();
            }
        };
    }

    private static void health(HttpExchange exchange) throws IOException {
        json(exchange, 200, Map.of("status", "ok", "service", "limitforge-simulation-api"));
    }

    private static void openApi(HttpExchange exchange) throws IOException {
        try (InputStream input = SimulationServer.class.getResourceAsStream("/openapi.json")) {
            if (input == null) throw new IOException("OpenAPI document is unavailable");
            byte[] body = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static void simulate(HttpExchange exchange) throws IOException {
        requireJson(exchange);
        byte[] body = readBounded(exchange.getRequestBody());
        final SimulationRequest request;
        try {
            request = JSON.readValue(body, SimulationRequest.class);
        } catch (JsonProcessingException e) {
            throw new RequestException(400, "INVALID_JSON", "Request body does not match the API schema");
        }

        validate(request);
        Map<String, Client> clients = request.clients().stream().collect(Collectors.toMap(
            ClientInput::clientId,
            SimulationServer::client,
            (first, ignored) -> {
                throw new RequestException(400, "DUPLICATE_CLIENT", "Client IDs must be unique");
            },
            LinkedHashMap::new
        ));
        Map<String, Instrument> instruments = request.instruments().stream().collect(Collectors.toMap(
            InstrumentInput::instrumentId,
            input -> new Instrument(input.instrumentId(), input.currency(), input.lotSize()),
            (first, ignored) -> {
                throw new RequestException(400, "DUPLICATE_INSTRUMENT", "Instrument IDs must be unique");
            },
            LinkedHashMap::new
        ));
        List<Order> orders = request.orders().stream().map(SimulationServer::order).toList();

        OrderMatchingEngine engine = new OrderMatchingEngine(clients, instruments);
        try {
            engine.processOrders(orders);
            json(exchange, 200, response(engine));
        } finally {
            engine.shutdown();
        }
    }

    private static Client client(ClientInput input) {
        Client client = new Client(
            input.clientId(),
            Set.copyOf(input.currencies()),
            input.positionCheck(),
            input.rating()
        );
        if (input.positions() != null) {
            input.positions().forEach(client::updatePosition);
        }
        return client;
    }

    private static Order order(OrderInput input) {
        final LocalTime time;
        final Order.Side side;
        final Order.Type type;
        try {
            time = LocalTime.parse(input.time());
            side = Order.Side.valueOf(input.side().toUpperCase());
            type = Order.Type.valueOf(input.type().toUpperCase());
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new RequestException(400, "INVALID_ORDER", "Order time, side, or type is invalid");
        }

        try {
            if (type == Order.Type.MARKET) {
                return Order.market(
                    input.orderId(), input.clientId(), input.instrumentId(), input.quantity(), side, time
                );
            }
            if (input.price() == null) {
                throw new RequestException(400, "INVALID_ORDER", "Limit orders require a price");
            }
            return Order.limit(
                input.orderId(), input.clientId(), input.instrumentId(), input.quantity(),
                input.price(), side, time
            );
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new RequestException(400, "INVALID_ORDER", "Order fields are invalid");
        }
    }

    private static SimulationResponse response(OrderMatchingEngine engine) {
        List<ExecutionOutput> executions = engine.getTransactions().stream()
            .map(SimulationServer::execution)
            .toList();
        List<RejectionOutput> rejections = engine.getRejections().stream()
            .map(entry -> new RejectionOutput(entry.getKey(), entry.getValue()))
            .toList();
        List<PositionOutput> positions = engine.getClients().values().stream()
            .sorted(Comparator.comparing(Client::getClientId))
            .flatMap(client -> client.getPositions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue() != 0)
                .map(entry -> new PositionOutput(
                    client.getClientId(), entry.getKey(), entry.getValue()
                )))
            .toList();
        return new SimulationResponse(
            executions,
            rejections,
            positions,
            new TreeMap<>(engine.getOpenPrices()),
            new TreeMap<>(engine.getClosePrices())
        );
    }

    private static ExecutionOutput execution(Transaction transaction) {
        return new ExecutionOutput(
            transaction.getTime().toString(),
            transaction.getInstrumentId(),
            transaction.getPrice(),
            transaction.getQuantity(),
            transaction.getFromClientId(),
            transaction.getToClientId()
        );
    }

    private static void validate(SimulationRequest request) {
        if (request.clients() == null || request.clients().isEmpty()) {
            throw new RequestException(400, "INVALID_REQUEST", "At least one client is required");
        }
        if (request.instruments() == null || request.instruments().isEmpty()) {
            throw new RequestException(400, "INVALID_REQUEST", "At least one instrument is required");
        }
        if (request.orders() == null || request.orders().isEmpty()) {
            throw new RequestException(400, "INVALID_REQUEST", "At least one order is required");
        }
        if (request.orders().size() > 100_000) {
            throw new RequestException(413, "TOO_MANY_ORDERS", "At most 100000 orders are allowed");
        }
        for (ClientInput client : request.clients()) {
            if (blank(client.clientId()) || client.currencies() == null
                || client.currencies().isEmpty() || client.currencies().stream().anyMatch(SimulationServer::blank)) {
                throw new RequestException(400, "INVALID_CLIENT", "Client ID and currencies are required");
            }
            if (client.positions() != null && client.positions().entrySet().stream()
                .anyMatch(entry -> blank(entry.getKey()) || entry.getValue() == null)) {
                throw new RequestException(400, "INVALID_CLIENT", "Client positions are invalid");
            }
        }
        for (InstrumentInput instrument : request.instruments()) {
            if (blank(instrument.instrumentId()) || blank(instrument.currency()) || instrument.lotSize() <= 0) {
                throw new RequestException(400, "INVALID_INSTRUMENT", "Instrument fields are invalid");
            }
        }
        for (OrderInput order : request.orders()) {
            if (blank(order.time()) || blank(order.orderId()) || blank(order.instrumentId())
                || blank(order.clientId()) || blank(order.type()) || blank(order.side())
                || order.quantity() <= 0) {
                throw new RequestException(400, "INVALID_ORDER", "Required order fields are invalid");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireJson(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new RequestException(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        byte[] body = input.readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new RequestException(413, "REQUEST_TOO_LARGE", "Request body exceeds 1 MiB");
        }
        return body;
    }

    private static void json(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void error(HttpExchange exchange, int status, String code, String message)
        throws IOException {
        json(exchange, status, Map.of("error", Map.of("code", code, "message", message)));
    }

    @FunctionalInterface
    private interface ExchangeAction {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class RequestException extends RuntimeException {
        private final int status;
        private final String code;

        private RequestException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        private int status() {
            return status;
        }

        private String code() {
            return code;
        }
    }

    public record SimulationRequest(
        List<ClientInput> clients,
        List<InstrumentInput> instruments,
        List<OrderInput> orders
    ) {
    }

    public record ClientInput(
        String clientId,
        List<String> currencies,
        boolean positionCheck,
        int rating,
        Map<String, Integer> positions
    ) {
    }

    public record InstrumentInput(String instrumentId, String currency, int lotSize) {
    }

    public record OrderInput(
        String time,
        String orderId,
        String instrumentId,
        int quantity,
        String clientId,
        String type,
        BigDecimal price,
        String side
    ) {
    }

    public record SimulationResponse(
        List<ExecutionOutput> executions,
        List<RejectionOutput> rejections,
        List<PositionOutput> positions,
        Map<String, BigDecimal> openPrices,
        Map<String, BigDecimal> closePrices
    ) {
    }

    public record ExecutionOutput(
        String time,
        String instrument,
        BigDecimal price,
        int quantity,
        String seller,
        String buyer
    ) {
    }

    public record RejectionOutput(String orderId, String reason) {
    }

    public record PositionOutput(String clientId, String instrument, int netPosition) {
    }

    static final class RunningServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private RunningServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        void start() {
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
