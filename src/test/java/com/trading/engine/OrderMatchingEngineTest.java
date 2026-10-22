package com.trading.engine;

import com.trading.model.Client;
import com.trading.model.Instrument;
import com.trading.model.Order;
import com.trading.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        assertEquals(new BigDecimal("10.0"), transactions.get(0).getPrice());
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
        assertEquals(new BigDecimal("10.0"), engine.getTransactions().get(0).getPrice());
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

        assertEquals(new BigDecimal("10.0"), engine.getOpenPrices().get("SIA"));
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
        assertEquals(new BigDecimal("11.0"), engine.getTransactions().get(0).getPrice());
        assertEquals(LocalTime.of(10, 0), engine.getTransactions().get(0).getTime());
    }

    @Test
    void eveningAuctionTradesAtTheOfficialCloseTime() {
        Order sell = order("S1", "SELLER", 100, 10.0, Order.Side.SELL, "16:00:01");
        Order buy = order("B1", "BUYER", 100, 10.0, Order.Side.BUY, "16:01:00");

        engine.processOrders(List.of(sell, buy));

        assertEquals(new BigDecimal("10.0"), engine.getClosePrices().get("SIA"));
        assertEquals(1, engine.getTransactions().size());
        assertEquals(LocalTime.of(16, 10), engine.getTransactions().get(0).getTime());
    }

    @Test
    void rejectsCurrencyAndLotSizeViolations() {
        Order wrongCurrency = Order.limit(
            "C1", "BUYER", "USD_STOCK", 100, new BigDecimal("10.0"),
            Order.Side.BUY, LocalTime.of(10, 0)
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

    @Test
    void reservesHoldingsForOpenSellOrders() {
        Client restrictedSeller = new Client("RESTRICTED", Set.of("SGD"), true, 1);
        restrictedSeller.updatePosition("SIA", 100);
        engine.shutdown();
        engine = new OrderMatchingEngine(
            Map.of(
                "RESTRICTED", restrictedSeller,
                "BUYER", new Client("BUYER", Set.of("SGD"), false, 1)
            ),
            Map.of("SIA", new Instrument("SIA", "SGD", 100))
        );

        Order firstSell = order(
            "S1", "RESTRICTED", 100, 10.0, Order.Side.SELL, "10:00:00"
        );
        Order secondSell = order(
            "S2", "RESTRICTED", 100, 11.0, Order.Side.SELL, "10:01:00"
        );

        engine.processOrders(List.of(firstSell, secondSell));

        assertEquals(Order.Status.NEW, firstSell.getStatus());
        assertEquals(Order.Status.REJECTED, secondSell.getStatus());
        assertEquals("REJECTED-POSITION CHECK FAILED", secondSell.getRejectionReason());
    }

    @Test
    void processesUnsortedContinuousOrdersChronologically() {
        Order lateSell = order("S2", "SELLER2", 100, 9.0, Order.Side.SELL, "15:00:00");
        Order buy = order("B1", "BUYER", 100, 11.0, Order.Side.BUY, "10:00:00");
        Order earlySell = order("S1", "SELLER", 100, 10.0, Order.Side.SELL, "09:31:00");

        engine.processOrders(List.of(lateSell, buy, earlySell));

        assertEquals(1, engine.getTransactions().size());
        Transaction execution = engine.getTransactions().get(0);
        assertEquals("SELLER", execution.getFromClientId());
        assertEquals(new BigDecimal("10.0"), execution.getPrice());
        assertEquals(LocalTime.of(10, 0), execution.getTime());
        assertEquals(100, lateSell.getRemainingQuantity());
    }

    @Test
    void usesOrderIdToResolveRestingPriceWhenTimestampsTie() {
        Order sell = order("A1", "SELLER", 100, 10.0, Order.Side.SELL, "10:00:00");
        Order buy = order("B1", "BUYER", 100, 11.0, Order.Side.BUY, "10:00:00");

        engine.processOrders(List.of(buy, sell));

        assertEquals(1, engine.getTransactions().size());
        assertEquals(new BigDecimal("10.0"), engine.getTransactions().get(0).getPrice());
    }

    @Test
    void appliesPositionChecksAfterEarlierBuysEvenWhenInputIsUnsorted() {
        Client restrictedClient = new Client("RESTRICTED", Set.of("SGD"), true, 1);
        engine.shutdown();
        engine = new OrderMatchingEngine(
            Map.of(
                "RESTRICTED", restrictedClient,
                "BUYER", new Client("BUYER", Set.of("SGD"), false, 1),
                "SELLER", new Client("SELLER", Set.of("SGD"), false, 1)
            ),
            Map.of("SIA", new Instrument("SIA", "SGD", 100))
        );
        Order laterSell = order("S2", "RESTRICTED", 100, 10.0, Order.Side.SELL, "11:00:00");
        Order earlySell = order("S1", "SELLER", 100, 10.0, Order.Side.SELL, "09:31:00");
        Order earlyBuy = order("B1", "RESTRICTED", 100, 10.0, Order.Side.BUY, "10:00:00");
        Order laterBuy = order("B2", "BUYER", 100, 10.0, Order.Side.BUY, "12:00:00");

        engine.processOrders(List.of(laterSell, laterBuy, earlyBuy, earlySell));

        assertTrue(engine.getRejections().isEmpty());
        assertEquals(2, engine.getTransactions().size());
        assertEquals(0, restrictedClient.getPosition("SIA"));
        assertEquals(Order.Status.FILLED, laterSell.getStatus());
    }

    @Test
    void opposingMarketOrdersDoNotBlockIncomingLimitSell() {
        Order marketBuy = marketOrder("B1", "BUYER", 100, Order.Side.BUY, "10:00:00");
        Order marketSell = marketOrder("S1", "SELLER", 100, Order.Side.SELL, "10:01:00");
        Order limitSell = order("S2", "SELLER2", 100, 10.0, Order.Side.SELL, "10:02:00");

        engine.processOrders(List.of(marketBuy, marketSell, limitSell));

        assertEquals(1, engine.getTransactions().size());
        Transaction execution = engine.getTransactions().get(0);
        assertEquals("SELLER2", execution.getFromClientId());
        assertEquals("BUYER", execution.getToClientId());
        assertEquals(LocalTime.of(10, 2), execution.getTime());
        assertEquals(new BigDecimal("10.0"), execution.getPrice());
        assertEquals(100, marketSell.getRemainingQuantity());
    }

    @Test
    void opposingMarketOrdersDoNotBlockIncomingLimitBuy() {
        Order marketSell = marketOrder("S1", "SELLER", 100, Order.Side.SELL, "10:00:00");
        Order marketBuy = marketOrder("B1", "BUYER", 100, Order.Side.BUY, "10:01:00");
        Order limitBuy = order("B2", "SELLER2", 100, 10.0, Order.Side.BUY, "10:02:00");

        engine.processOrders(List.of(marketSell, marketBuy, limitBuy));

        assertEquals(1, engine.getTransactions().size());
        Transaction execution = engine.getTransactions().get(0);
        assertEquals("SELLER", execution.getFromClientId());
        assertEquals("SELLER2", execution.getToClientId());
        assertEquals(LocalTime.of(10, 2), execution.getTime());
        assertEquals(100, marketBuy.getRemainingQuantity());
    }

    @Test
    void rejectsOrdersAtOrAfterTheClosingAuctionEnds() {
        Order beforeClose = order("S1", "SELLER", 100, 10.0, Order.Side.SELL, "16:09:59");
        Order atClose = order("B1", "BUYER", 100, 10.0, Order.Side.BUY, "16:10:00");
        Order afterClose = order("B2", "BUYER", 100, 10.0, Order.Side.BUY, "17:00:00");

        engine.processOrders(List.of(beforeClose, atClose, afterClose));

        assertEquals(Order.Status.NEW, beforeClose.getStatus());
        assertEquals("REJECTED-MARKET CLOSED", atClose.getRejectionReason());
        assertEquals("REJECTED-MARKET CLOSED", afterClose.getRejectionReason());
        assertEquals(2, engine.getRejections().size());
        assertTrue(engine.getTransactions().isEmpty());
    }

    @Test
    void keepsOpposingMarketOrdersUnfilledWithoutAPrice() {
        Order buy = marketOrder("B1", "BUYER", 100, Order.Side.BUY, "10:00:00");
        Order sell = marketOrder("S1", "SELLER", 100, Order.Side.SELL, "10:01:00");

        engine.processOrders(List.of(buy, sell));

        assertTrue(engine.getTransactions().isEmpty());
        assertTrue(engine.getOpenPrices().isEmpty());
        assertTrue(engine.getClosePrices().isEmpty());
        assertEquals(Order.Status.NEW, buy.getStatus());
        assertEquals(Order.Status.NEW, sell.getStatus());
    }

    @Test
    void preservesPositionsWhenAValidFillExceedsTheIntegerRange() {
        Client buyer = new Client("BUYER", Set.of("SGD"), false, 1);
        buyer.updatePosition("SIA", Integer.MAX_VALUE);
        engine.shutdown();
        engine = new OrderMatchingEngine(
            Map.of("BUYER", buyer, "SELLER", new Client("SELLER", Set.of("SGD"), false, 1)),
            Map.of("SIA", new Instrument("SIA", "SGD", 1))
        );

        engine.processOrders(List.of(
            order("S1", "SELLER", 1, 10.0, Order.Side.SELL, "10:00:00"),
            order("B1", "BUYER", 1, 10.0, Order.Side.BUY, "10:01:00")
        ));

        assertEquals(2_147_483_648L, buyer.getPosition("SIA"));
        assertEquals(-1L, engine.getClients().get("SELLER").getPosition("SIA"));
        assertEquals(1, engine.getTransactions().size());
    }

    @Test
    void accumulatesMultipleMaximumSizedFillsWithoutOverflow() {
        engine.shutdown();
        engine = new OrderMatchingEngine(
            Map.of(
                "BUYER", new Client("BUYER", Set.of("SGD"), false, 1),
                "SELLER", new Client("SELLER", Set.of("SGD"), false, 1)
            ),
            Map.of("SIA", new Instrument("SIA", "SGD", 1))
        );

        engine.processOrders(List.of(
            order("S1", "SELLER", Integer.MAX_VALUE, 10.0, Order.Side.SELL, "10:00:00"),
            order("B1", "BUYER", Integer.MAX_VALUE, 10.0, Order.Side.BUY, "10:01:00"),
            order("S2", "SELLER", Integer.MAX_VALUE, 10.0, Order.Side.SELL, "11:00:00"),
            order("B2", "BUYER", Integer.MAX_VALUE, 10.0, Order.Side.BUY, "11:01:00")
        ));

        assertEquals(4_294_967_294L, engine.getClients().get("BUYER").getPosition("SIA"));
        assertEquals(-4_294_967_294L, engine.getClients().get("SELLER").getPosition("SIA"));
        assertEquals(2, engine.getTransactions().size());
    }

    @Test
    void reservesLargeAggregateHoldingsWithoutOverflow() {
        Client restricted = new Client("RESTRICTED", Set.of("SGD"), true, 1);
        restricted.updatePosition("SIA", Integer.MAX_VALUE);
        restricted.updatePosition("SIA", Integer.MAX_VALUE);
        engine.shutdown();
        engine = new OrderMatchingEngine(
            Map.of("RESTRICTED", restricted),
            Map.of("SIA", new Instrument("SIA", "SGD", 1))
        );
        Order first = order("S1", "RESTRICTED", Integer.MAX_VALUE, 10.0, Order.Side.SELL, "10:00:00");
        Order second = order("S2", "RESTRICTED", Integer.MAX_VALUE, 11.0, Order.Side.SELL, "11:00:00");
        Order excess = order("S3", "RESTRICTED", 1, 12.0, Order.Side.SELL, "12:00:00");

        engine.processOrders(List.of(first, second, excess));

        assertEquals(Order.Status.NEW, first.getStatus());
        assertEquals(Order.Status.NEW, second.getStatus());
        assertEquals("REJECTED-POSITION CHECK FAILED", excess.getRejectionReason());
        assertEquals(1, engine.getRejections().size());
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
        return Order.limit(
            orderId,
            clientId,
            "SIA",
            quantity,
            BigDecimal.valueOf(price),
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
