package com.trading.csv;

import com.trading.model.Client;
import com.trading.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CSVWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesClientPositionsInDeterministicOrder() throws IOException {
        Client clientB = new Client("B", Set.of("SGD"), false, 2);
        clientB.updatePosition("SIA", -100);
        Client clientA = new Client("A", Set.of("SGD"), false, 1);
        clientA.updatePosition("SIA", 100);
        Map<String, Client> clients = new LinkedHashMap<>();
        clients.put("B", clientB);
        clients.put("A", clientA);

        Path output = tempDir.resolve("clients.csv");
        CSVWriter.writeClientReport(output.toString(), clients);

        assertEquals(
            List.of(
                "ClientID,InstrumentID,NetPosition",
                "A,SIA,100",
                "B,SIA,-100"
            ),
            Files.readAllLines(output)
        );
    }

    @Test
    void writesInstrumentStatisticsFromTransactions() throws IOException {
        List<Transaction> transactions = List.of(
            new Transaction("S", "B", "SIA", 100, new BigDecimal("10.0"), LocalTime.NOON),
            new Transaction("S", "B", "SIA", 300, new BigDecimal("12.0"), LocalTime.NOON)
        );

        Path output = tempDir.resolve("instruments.csv");
        CSVWriter.writeInstrumentReport(
            output.toString(),
            Map.of("SIA", new BigDecimal("10.0")),
            Map.of("SIA", new BigDecimal("12.0")),
            Map.of("SIA", transactions),
            List.of("SIA")
        );

        assertEquals(
            List.of(
                "\"#\",InstrumentID,OpenPrice,ClosePrice,TotalVolume,VWAP,DayHigh,DayLow",
                "1,SIA,10.0,12.0,400,11.5,12.0,10.0"
            ),
            Files.readAllLines(output)
        );
    }
}
