package com.trading.csv;

import com.trading.model.Client;
import com.trading.model.Transaction;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Writes deterministic CSV reports produced by the sample application. */
public final class CSVWriter {
    private CSVWriter() {
    }

    public static void writeExchangeReport(
        String fileName,
        List<Map.Entry<String, String>> rejections
    ) throws IOException {
        try (CSVPrinter printer = printer(fileName, "OrderID", "RejectionReason")) {
            for (Map.Entry<String, String> rejection : rejections) {
                printer.printRecord(rejection.getKey(), rejection.getValue());
            }
        }
    }

    public static void writeClientReport(String fileName, Map<String, Client> clients)
        throws IOException {
        try (CSVPrinter printer = printer(fileName, "ClientID", "InstrumentID", "NetPosition")) {
            List<Client> sortedClients = clients.values().stream()
                .sorted(Comparator.comparing(Client::getClientId))
                .toList();

            for (Client client : sortedClients) {
                List<Map.Entry<String, Integer>> positions = new ArrayList<>(
                    client.getPositions().entrySet()
                );
                positions.sort(Map.Entry.comparingByKey());
                for (Map.Entry<String, Integer> position : positions) {
                    if (position.getValue() != 0) {
                        printer.printRecord(
                            client.getClientId(),
                            position.getKey(),
                            position.getValue()
                        );
                    }
                }
            }
        }
    }

    public static void writeInstrumentReport(
        String fileName,
        Map<String, Double> openPrices,
        Map<String, Double> closePrices,
        Map<String, List<Transaction>> transactionsByInstrument,
        List<String> instrumentIds
    ) throws IOException {
        try (CSVPrinter printer = printer(
            fileName,
            "#",
            "InstrumentID",
            "OpenPrice",
            "ClosePrice",
            "TotalVolume",
            "VWAP",
            "DayHigh",
            "DayLow"
        )) {
            int rowNumber = 1;
            for (String instrumentId : instrumentIds) {
                List<Transaction> transactions = transactionsByInstrument.getOrDefault(
                    instrumentId,
                    List.of()
                );
                long totalVolume = transactions.stream().mapToLong(Transaction::getQuantity).sum();
                double tradedValue = transactions.stream()
                    .mapToDouble(transaction -> transaction.getPrice() * transaction.getQuantity())
                    .sum();

                Object vwap = totalVolume == 0 ? "" : tradedValue / totalVolume;
                Object high = transactions.stream()
                    .mapToDouble(Transaction::getPrice)
                    .max()
                    .stream()
                    .boxed()
                    .findFirst()
                    .orElse(null);
                Object low = transactions.stream()
                    .mapToDouble(Transaction::getPrice)
                    .min()
                    .stream()
                    .boxed()
                    .findFirst()
                    .orElse(null);

                printer.printRecord(
                    rowNumber++,
                    instrumentId,
                    openPrices.getOrDefault(instrumentId, null),
                    closePrices.getOrDefault(instrumentId, null),
                    totalVolume,
                    vwap,
                    high,
                    low
                );
            }
        }
    }

    private static CSVPrinter printer(String fileName, String... headers) throws IOException {
        Path path = Path.of(fileName);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        try {
            return new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(headers).build());
        } catch (RuntimeException | IOException e) {
            writer.close();
            throw e;
        }
    }
}
