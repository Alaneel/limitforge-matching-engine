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
        Order marketSell = marketOrder(
            "S1", "SELLER", 100, Order.Side.SELL, "09:31:00"
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
    void auctionChoosesPriceWithMaximumExecutableVolume() {
        List<Order> orders = List.of(
            marketOrder("B1", "BUYER", 100, Order.Side.BUY, "09:00:00"),
            order("B2", "BUYER", 100, 10.0, Order.Side.BUY, "09:01:00"),
            order("S1", "SELLER", 100, 9.0, Order.Side.SELL, "09:02:00"),
            order("S2", "SELLER2", 100, 10.0, Order.Side.SELL, "09:03:00")
        );

        engine.processOrders(orders);

        assertEquals(10.0, engine.getOpenPrices().get("SIA"));
        assertEquals(2, engine.getTransactions().size());
        assertTrue(engine.getTransactions().stream()
            .allMatch(transaction -> transaction.getTime().equals(LocalTime.of(9, 30))));
    }

    @Test
    void carriesUnmatchedMorningOrdersIntoContinuousTrading() {
        Order morningSell = order("S1", "SELLER", 100, 11.0, Order.Side.SELL, "09:00:00");
        Order morningBuy = order("B1", "BUYER", 100, 10.0, Order.Side.BUY, "09:01:00");
        Order laterBuy = order("B2", "BUYER", 100, 11.0, Order.Side.BUY, "10:00:00");

        engine.processOrders(List.of(morningSell, morningBuy, laterBuy));

        assertEquals(1, engine.getTransactions().size());
        assertEquals(11.0, engine.getTransactions().get(0).getPrice());
        assertEquals(LocalTime.of(10, 0), engine.getTransactions().get(0).getTime());
    }

    @Test
    void eveningAuctionTradesAtTheOfficialCloseTime() {
        Order sell = order("S1", "SELLER", 100, 10.0, Order.Side.SELL, "16:00:01");
        Order buy = order("B1", "BUYER", 100, 10.0, Order.Side.BUY, "16:01:00");

        engine.processOrders(List.of(sell, buy));

        assertEquals(10.0, engine.getClosePrices().get("SIA"));
        assertEquals(1, engine.getTransactions().size());
        assertEquals(LocalTime.of(16, 10), engine.getTransactions().get(0).getTime());
    }

    @Test
    void rejectsCurrencyAndLotSizeViolations() {
        Order wrongCurrency = Order.limit(
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

    @Test
    void rejectsDuplicateOrderIdsAcrossBatches() {
        Order original = order("DUP-1", "BUYER", 100, 10.0, Order.Side.BUY, "10:00:00");
        Order duplicate = order("DUP-1", "BUYER", 100, 11.0, Order.Side.BUY, "10:01:00");

        engine.processOrders(List.of(original));
        engine.processOrders(List.of(duplicate));

        assertEquals(Order.Status.NEW, original.getStatus());
        assertEquals(Order.Status.REJECTED, duplicate.getStatus());
        assertEquals("REJECTED-DUPLICATE ORDER ID", duplicate.getRejectionReason());
    }

    private Order order(
        String orderId,
        String clientId,
        int quantity,
        double price,
        Order.Side side,
        String time
    ) {
        return Order.limit(
            orderId,
            clientId,
            "SIA",
            quantity,
            price,
            side,
            LocalTime.parse(time)
        );
    }

    private Order marketOrder(
        String orderId,
        String clientId,
        int quantity,
        Order.Side side,
        String time
    ) {
        return Order.market(
            orderId,
            clientId,
            "SIA",
            quantity,
            side,
            LocalTime.parse(time)
        );
    }
}
