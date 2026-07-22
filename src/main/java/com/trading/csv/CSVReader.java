package com.trading.csv;

import com.trading.model.Client;
import com.trading.model.Instrument;
import com.trading.model.Order;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads and validates the CSV input files used by the sample application. */
public final class CSVReader {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final CSVFormat INPUT_FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreSurroundingSpaces(true)
        .setTrim(true)
        .build();

    private CSVReader() {
    }

    public static List<Client> readClients(String fileName) throws IOException {
        List<Client> clients = new ArrayList<>();
        try (CSVParser parser = parse(fileName)) {
            for (CSVRecord record : parser) {
                String clientId = required(record, "ClientID");
                Set<String> currencies = new LinkedHashSet<>();
                Arrays.stream(required(record, "Currencies").split(","))
                    .map(String::trim)
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .filter(value -> !value.isEmpty())
                    .forEach(currencies::add);

                if (currencies.isEmpty()) {
                    throw invalid(record, "Currencies must contain at least one value");
                }

                String positionCheckValue = required(record, "PositionCheck");
                if (!positionCheckValue.equalsIgnoreCase("Y")
                    && !positionCheckValue.equalsIgnoreCase("N")) {
                    throw invalid(record, "PositionCheck must be Y or N");
                }

                int rating = positiveInt(record, "Rating");
                clients.add(new Client(
                    clientId,
                    currencies,
                    positionCheckValue.equalsIgnoreCase("Y"),
                    rating
                ));
            }
        }
        return clients;
    }

    public static List<Instrument> readInstruments(String fileName) throws IOException {
        List<Instrument> instruments = new ArrayList<>();
        try (CSVParser parser = parse(fileName)) {
            for (CSVRecord record : parser) {
                instruments.add(new Instrument(
                    required(record, "InstrumentID"),
                    required(record, "Currency").toUpperCase(Locale.ROOT),
                    positiveInt(record, "LotSize")
                ));
            }
        }
        return instruments;
    }

    public static List<Order> readOrders(String fileName) throws IOException {
        List<Order> orders = new ArrayList<>();
        try (CSVParser parser = parse(fileName)) {
            for (CSVRecord record : parser) {
                String rawPrice = required(record, "Price");
                double price;
                if (rawPrice.equalsIgnoreCase("Market")) {
                    price = Double.MAX_VALUE;
                } else {
                    price = positiveDouble(record, "Price");
                }

                Order.Side side;
                String rawSide = required(record, "Side");
                if (rawSide.equalsIgnoreCase("Buy")) {
                    side = Order.Side.BUY;
                } else if (rawSide.equalsIgnoreCase("Sell")) {
                    side = Order.Side.SELL;
                } else {
                    throw invalid(record, "Side must be Buy or Sell");
                }

                LocalTime time;
                try {
                    time = LocalTime.parse(required(record, "Time"), TIME_FORMAT);
                } catch (DateTimeParseException e) {
                    throw invalid(record, "Time must use H:mm:ss format", e);
                }

                orders.add(new Order(
                    required(record, "OrderID"),
                    required(record, "Client"),
                    required(record, "Instrument"),
                    positiveInt(record, "Quantity"),
                    price,
                    side,
                    time
                ));
            }
        }
        return orders;
    }

    private static CSVParser parse(String fileName) throws IOException {
        Path path = Path.of(fileName);
        if (!Files.isRegularFile(path)) {
            throw new IOException("CSV input file not found: " + path.toAbsolutePath());
        }
        Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            return INPUT_FORMAT.parse(reader);
        } catch (RuntimeException | IOException e) {
            reader.close();
            throw e;
        }
    }

    private static String required(CSVRecord record, String column) throws IOException {
        final String value;
        try {
            value = record.get(column).trim();
        } catch (IllegalArgumentException e) {
            throw invalid(record, "Missing required column: " + column, e);
        }
        if (value.isEmpty()) {
            throw invalid(record, column + " must not be blank");
        }
        return value;
    }

    private static int positiveInt(CSVRecord record, String column) throws IOException {
        String value = required(record, column);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw invalid(record, column + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid(record, column + " must be a whole number", e);
        }
    }

    private static double positiveDouble(CSVRecord record, String column) throws IOException {
        String value = required(record, column);
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0) {
                throw invalid(record, column + " must be a finite number greater than zero");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid(record, column + " must be a number or Market", e);
        }
    }

    private static IOException invalid(CSVRecord record, String message) {
        return new IOException("Invalid CSV record at line " + record.getRecordNumber() + ": " + message);
    }

    private static IOException invalid(CSVRecord record, String message, Exception cause) {
        return new IOException(
            "Invalid CSV record at line " + record.getRecordNumber() + ": " + message,
            cause
        );
    }
}
