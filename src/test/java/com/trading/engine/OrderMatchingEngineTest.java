package com.trading.engine;

import com.trading.model.Client;
import com.trading.model.Instrument;
import com.trading.model.Order;
import com.trading.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderMatchingEngineTest {
    private OrderMatchingEngine engine;

    @BeforeEach
    void setUp() {
        Map<String, Client> clients = Map.of(
            "BUYER", new Client("BUYER", Set.of("SGD"), false, 1),
            "SELLER", new Client("SELLER", Set.of("SGD"), false, 1),
            "SELLER2", new Client("SELLER2", Set.of("SGD"), false, 10)
        );
        Map<String, Instrument> instruments = Map.of(
            "SIA", new Instrument("SIA", "SGD", 100)
        );
        engine = new OrderMatchingEngine(clients, instruments);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    void matchesCrossingLimitOrdersAndUpdatesPositions() {
        Order sell = order("S1", "SELLER", 100, 10.0, Order.Side.SELL, "09:31:00");
        Order buy = order("B1", "BUYER", 100, 11.0, Order.Side.BUY, "09:32:00");

        engine.processOrders(List.of(sell, buy));

        List<Transaction> transactions = engine.getTransactions();
        assertEquals(1, transactions.size());
        assertEquals(100, transactions.get(0).getQuantity());
        assertEquals(10.0, transactions.get(0).getPrice());
        assertEquals(100, engine.getClients().get("BUYER").getPosition("SIA"));
        assertEquals(-100, engine.getClients().get("SELLER").getPosition("SIA"));
    }

    @Test
    void supportsPartialFills() {
        Order sell = order("S1", "SELLER", 200, 10.0, Order.Side.SELL, "09:31:00");
        Order buy = order("B1", "BUYER", 100, 10.0, Order.Side.BUY, "09:32:00");

        engine.processOrders(List.of(sell, buy));

        assertEquals(1, engine.getTransactions().size());
        assertEquals(100, sell.getRemainingQuantity());
        assertEquals(0, buy.getRemainingQuantity());
    }

    @Test
    void matchesMarketSellAtOpposingLimitPrice() {
        Order marketSell = order(
            "S1", "SELLER", 100, Double.MAX_VALUE, Order.Side.SELL, "09:31:00"
        );
        Order limitBuy = order("B1", "BUYER", 100, 10.0, Order.Side.BUY, "09:32:00");

        engine.processOrders(List.of(marketSell, limitBuy));

        assertEquals(1, engine.getTransactions().size());
        assertEquals(10.0, engine.getTransactions().get(0).getPrice());
        assertEquals(LocalTime.of(9, 32), engine.getTransactions().get(0).getTime());
    }

    @Test
    void givesOlderOrderPriorityAtTheSamePrice() {
        Order olderSell = order("S1", "SELLER", 100, 10.0, Order.Side.SELL, "09:31:00");
        Order newerSell = order("S2", "SELLER2", 100, 10.0, Order.Side.SELL, "09:31:01");
        Order buy = order("B1", "BUYER", 100, 10.0, Order.Side.BUY, "09:32:00");

        engine.processOrders(List.of(newerSell, olderSell, buy));

        assertEquals(1, engine.getTransactions().size());
        assertEquals("SELLER", engine.getTransactions().get(0).getFromClientId());
        assertEquals(100, newerSell.getRemainingQuantity());
    }

    @Test
    void rejectsCurrencyAndLotSizeViolations() {
        Order wrongCurrency = new Order(
            "C1", "BUYER", "USD_STOCK", 100, 10.0, Order.Side.BUY, LocalTime.of(10, 0)
        );
        Order wrongLot = order("L1", "BUYER", 50, 10.0, Order.Side.BUY, "10:00:01");

        Map<String, Client> clients = Map.of(
            "BUYER", new Client("BUYER", Set.of("SGD"), false, 1)
        );
        Map<String, Instrument> instruments = Map.of(
            "SIA", new Instrument("SIA", "SGD", 100),
            "USD_STOCK", new Instrument("USD_STOCK", "USD", 1)
        );
        engine.shutdown();
        engine = new OrderMatchingEngine(clients, instruments);

        engine.processOrders(List.of(wrongCurrency, wrongLot));

        assertEquals(2, engine.getRejections().size());
        assertTrue(engine.getTransactions().isEmpty());
    }

    @Test
    void rejectsUnknownClientsInsteadOfThrowing() {
        Order order = order("U1", "UNKNOWN", 100, 10.0, Order.Side.BUY, "10:00:00");

        engine.processOrders(List.of(order));

        assertEquals(1, engine.getRejections().size());
        assertEquals("U1", engine.getRejections().get(0).getKey());
        assertEquals("REJECTED-CLIENT NOT FOUND", engine.getRejections().get(0).getValue());
        assertTrue(engine.getTransactions().isEmpty());
    }

    private Order order(
        String orderId,
        String clientId,
        int quantity,
        double price,
        Order.Side side,
        String time
    ) {
        return new Order(
            orderId,
            clientId,
            "SIA",
            quantity,
            price,
            side,
            LocalTime.parse(time)
        );
    }
}
