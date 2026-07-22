package com.trading.report;

import com.trading.model.Client;
import com.trading.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSnapshotWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDeterministicMachineReadableSession() throws Exception {
        Client buyer = new Client("BUYER", Set.of("SGD"), false, 1);
        Client seller = new Client("SELLER", Set.of("SGD"), false, 1);
        buyer.updatePosition("SIA", 100);
        seller.updatePosition("SIA", -100);
        Transaction transaction = new Transaction(
            "SELLER",
            "BUYER",
            "SIA",
            100,
            new BigDecimal("32.10"),
            LocalTime.of(9, 30)
        );
        Path output = tempDir.resolve("session.json");

        SessionSnapshotWriter.write(
            output.toString(),
            List.of(transaction),
            List.of(Map.entry("bad\"id", "REJECTED-TEST")),
            Map.of("SELLER", seller, "BUYER", buyer),
            Map.of("SIA", new BigDecimal("32.10")),
            Map.of()
        );

        String json = Files.readString(output);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"price\": 32.10"));
        assertTrue(json.contains("\"orderId\": \"bad\\\"id\""));
        assertTrue(json.indexOf("\"clientId\": \"BUYER\"")
            < json.indexOf("\"clientId\": \"SELLER\""));
        assertEquals(json, Files.readString(output));
    }
}
