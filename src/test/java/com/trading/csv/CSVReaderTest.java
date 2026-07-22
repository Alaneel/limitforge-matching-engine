package com.trading.csv;

import com.trading.model.Client;
import com.trading.model.Instrument;
import com.trading.model.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CSVReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsClientsInstrumentsAndOrders() throws IOException {
        Path clientsFile = write(
            "clients.csv",
            "ClientID,Currencies,PositionCheck,Rating\nA,\"USD,SGD\",Y,2\n"
        );
        Path instrumentsFile = write(
            "instruments.csv",
            "InstrumentID,Currency,LotSize\nSIA,SGD,100\n"
        );
        Path ordersFile = write(
            "orders.csv",
            "Time,OrderID,Instrument,Quantity,Client,Price,Side\n"
                + "9:30:01,A1,SIA,100,A,Market,Buy\n"
        );

        List<Client> clients = CSVReader.readClients(clientsFile.toString());
        List<Instrument> instruments = CSVReader.readInstruments(instrumentsFile.toString());
        List<Order> orders = CSVReader.readOrders(ordersFile.toString());

        assertEquals(1, clients.size());
        assertEquals("A", clients.get(0).getClientId());
        assertTrue(clients.get(0).getCurrencies().containsAll(List.of("USD", "SGD")));
        assertTrue(clients.get(0).isPositionCheck());

        assertEquals(100, instruments.get(0).getLotSize());
        assertEquals("SIA", instruments.get(0).getInstrumentId());

        assertEquals("A1", orders.get(0).getOrderId());
        assertEquals(Order.Side.BUY, orders.get(0).getSide());
        assertTrue(orders.get(0).isMarketOrder());
    }

    @Test
    void rejectsMalformedOrderRowsWithUsefulContext() throws IOException {
        Path ordersFile = write(
            "orders.csv",
            "Time,OrderID,Instrument,Quantity,Client,Price,Side\n"
                + "not-a-time,A1,SIA,100,A,10.5,Buy\n"
        );

        IOException exception = assertThrows(
            IOException.class,
            () -> CSVReader.readOrders(ordersFile.toString())
        );

        assertTrue(exception.getMessage().contains("Time must use H:mm:ss format"));
        assertTrue(exception.getMessage().contains("line 1"));
    }

    @Test
    void preservesDecimalPricesExactly() throws IOException {
        Path ordersFile = write(
            "orders.csv",
            "Time,OrderID,Instrument,Quantity,Client,Price,Side\n"
                + "9:30:01,A1,SIA,100,A,0.10,Buy\n"
        );

        Order order = CSVReader.readOrders(ordersFile.toString()).get(0);

        assertEquals(new BigDecimal("0.10"), order.getPrice());
    }

    @Test
    void rejectsMissingFilesClearly() {
        Path missingFile = tempDir.resolve("missing.csv");

        IOException exception = assertThrows(
            IOException.class,
            () -> CSVReader.readClients(missingFile.toString())
        );

        assertTrue(exception.getMessage().contains("CSV input file not found"));
    }

    private Path write(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content);
        return path;
    }
}
