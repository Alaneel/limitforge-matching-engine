package com.trading.report;

import com.trading.model.Client;
import com.trading.model.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Writes a deterministic JSON snapshot that can be consumed by demos and other tools. */
public final class SessionSnapshotWriter {
    private SessionSnapshotWriter() {
    }

    public static void write(
        String fileName,
        List<Transaction> transactions,
        List<Map.Entry<String, String>> rejections,
        Map<String, Client> clients,
        Map<String, BigDecimal> openPrices,
        Map<String, BigDecimal> closePrices
    ) throws IOException {
        Path path = Path.of(fileName);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJson(
            transactions,
            rejections,
            clients,
            openPrices,
            closePrices
        ), StandardCharsets.UTF_8);
    }

    static String toJson(
        List<Transaction> transactions,
        List<Map.Entry<String, String>> rejections,
        Map<String, Client> clients,
        Map<String, BigDecimal> openPrices,
        Map<String, BigDecimal> closePrices
    ) {
        long totalVolume = transactions.stream().mapToLong(Transaction::getQuantity).sum();
        BigDecimal tradedValue = transactions.stream()
            .map(transaction -> transaction.getPrice().multiply(
                BigDecimal.valueOf(transaction.getQuantity())
            ))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vwap = totalVolume == 0
            ? null
            : tradedValue.divide(BigDecimal.valueOf(totalVolume), MathContext.DECIMAL64);
        BigDecimal high = transactions.stream()
            .map(Transaction::getPrice)
            .max(BigDecimal::compareTo)
            .orElse(null);
        BigDecimal low = transactions.stream()
            .map(Transaction::getPrice)
            .min(BigDecimal::compareTo)
            .orElse(null);

        String instrumentId = transactions.stream()
            .map(Transaction::getInstrumentId)
            .findFirst()
            .orElseGet(() -> openPrices.keySet().stream().sorted().findFirst().orElse(""));

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": 1,\n");
        json.append("  \"engine\": \"LimitForge\",\n");
        json.append("  \"source\": \"input_orders.csv\",\n");
        json.append("  \"instrument\": {\n");
        field(json, "id", instrumentId, 4, true);
        numberField(json, "openPrice", openPrices.get(instrumentId), 4, true);
        numberField(json, "closePrice", closePrices.get(instrumentId), 4, true);
        json.append("    \"totalVolume\": ").append(totalVolume).append(",\n");
        numberField(json, "vwap", vwap, 4, true);
        numberField(json, "high", high, 4, true);
        numberField(json, "low", low, 4, false);
        json.append("  },\n");

        json.append("  \"executions\": [\n");
        for (int index = 0; index < transactions.size(); index++) {
            Transaction transaction = transactions.get(index);
            json.append("    {\n");
            json.append("      \"sequence\": ").append(index + 1).append(",\n");
            field(json, "time", transaction.getTime().toString(), 6, true);
            field(json, "instrument", transaction.getInstrumentId(), 6, true);
            numberField(json, "price", transaction.getPrice(), 6, true);
            json.append("      \"quantity\": ").append(transaction.getQuantity()).append(",\n");
            field(json, "seller", transaction.getFromClientId(), 6, true);
            field(json, "buyer", transaction.getToClientId(), 6, false);
            json.append("    }").append(index + 1 < transactions.size() ? "," : "").append("\n");
        }
        json.append("  ],\n");

        json.append("  \"rejections\": [\n");
        for (int index = 0; index < rejections.size(); index++) {
            Map.Entry<String, String> rejection = rejections.get(index);
            json.append("    {");
            inlineField(json, "orderId", rejection.getKey(), true);
            inlineField(json, "reason", rejection.getValue(), false);
            json.append("}").append(index + 1 < rejections.size() ? "," : "").append("\n");
        }
        json.append("  ],\n");

        List<Position> positions = new ArrayList<>();
        clients.values().stream()
            .sorted(Comparator.comparing(Client::getClientId))
            .forEach(client -> client.getPositions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(position -> position.getValue() != 0)
                .forEach(position -> positions.add(new Position(
                    client.getClientId(),
                    position.getKey(),
                    position.getValue()
                )))
            );

        json.append("  \"positions\": [\n");
        for (int index = 0; index < positions.size(); index++) {
            Position position = positions.get(index);
            json.append("    {");
            inlineField(json, "clientId", position.clientId(), true);
            inlineField(json, "instrument", position.instrumentId(), true);
            json.append(" \"netPosition\": ").append(position.netPosition());
            json.append("}").append(index + 1 < positions.size() ? "," : "").append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void field(
        StringBuilder json,
        String name,
        String value,
        int spaces,
        boolean comma
    ) {
        json.append(" ".repeat(spaces)).append('"').append(escape(name)).append("\": \"")
            .append(escape(value)).append('"').append(comma ? "," : "").append("\n");
    }

    private static void numberField(
        StringBuilder json,
        String name,
        BigDecimal value,
        int spaces,
        boolean comma
    ) {
        json.append(" ".repeat(spaces)).append('"').append(escape(name)).append("\": ")
            .append(value == null ? "null" : value.toPlainString())
            .append(comma ? "," : "").append("\n");
    }

    private static void inlineField(
        StringBuilder json,
        String name,
        String value,
        boolean comma
    ) {
        json.append(" \"").append(escape(name)).append("\": \"").append(escape(value))
            .append('"').append(comma ? "," : "");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private record Position(String clientId, String instrumentId, int netPosition) {
    }
}
