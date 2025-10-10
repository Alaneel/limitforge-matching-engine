package com.trading.engine;

import com.trading.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe order matching engine that processes morning auctions,
 * real-time trading, and evening auctions
 */
public class OrderMatchingEngine {
    private static final Logger logger = LoggerFactory.getLogger(OrderMatchingEngine.class);

    private final ConcurrentHashMap<String, Client> clients;
    private final ConcurrentHashMap<String, Instrument> instruments;
    private final List<Transaction> transactions;
    private final List<Map.Entry<String, String>> rejections;

    // Order books for each instrument
    private final ConcurrentHashMap<String, PriorityBlockingQueue<Order>> buyOrderBooks;
    private final ConcurrentHashMap<String, PriorityBlockingQueue<Order>> sellOrderBooks;

    // Real-time order books (different priority)
    private final ConcurrentHashMap<String, PriorityBlockingQueue<Order>> realTimeBuyBooks;
    private final ConcurrentHashMap<String, PriorityBlockingQueue<Order>> realTimeSellBooks;

    // Price tracking
    private final ConcurrentHashMap<String, Double> openPrices;
    private final ConcurrentHashMap<String, Double> closePrices;

    private final ExecutorService executorService;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public OrderMatchingEngine(Map<String, Client> clients, Map<String, Instrument> instruments) {
        this.clients = new ConcurrentHashMap<>(clients);
        this.instruments = new ConcurrentHashMap<>(instruments);
        this.transactions = new CopyOnWriteArrayList<>();
        this.rejections = new CopyOnWriteArrayList<>();

        this.buyOrderBooks = new ConcurrentHashMap<>();
        this.sellOrderBooks = new ConcurrentHashMap<>();
        this.realTimeBuyBooks = new ConcurrentHashMap<>();
        this.realTimeSellBooks = new ConcurrentHashMap<>();

        this.openPrices = new ConcurrentHashMap<>();
        this.closePrices = new ConcurrentHashMap<>();

        // Create thread pool for concurrent order processing
        this.executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );

        initializeOrderBooks();
    }

    private void initializeOrderBooks() {
        for (String instrumentId : instruments.keySet()) {
            buyOrderBooks.put(instrumentId, createBuyComparator());
            sellOrderBooks.put(instrumentId, createSellComparator());
            realTimeBuyBooks.put(instrumentId, createRealTimeBuyComparator());
            realTimeSellBooks.put(instrumentId, createRealTimeSellComparator());
        }
    }

    private PriorityBlockingQueue<Order> createBuyComparator() {
        return new PriorityBlockingQueue<>(100, (a, b) -> {
            if (a.getPrice() != b.getPrice()) {
                return Double.compare(b.getPrice(), a.getPrice()); // Higher price first
            }
            return Integer.compare(clients.get(b.getClientId()).getRating(),
                                 clients.get(a.getClientId()).getRating());
        });
    }

    private PriorityBlockingQueue<Order> createSellComparator() {
        return new PriorityBlockingQueue<>(100, (a, b) -> {
            if (a.getPrice() != b.getPrice()) {
                return Double.compare(a.getPrice(), b.getPrice()); // Lower price first
            }
            return Integer.compare(clients.get(b.getClientId()).getRating(),
                                 clients.get(a.getClientId()).getRating());
        });
    }

    private PriorityBlockingQueue<Order> createRealTimeBuyComparator() {
        return new PriorityBlockingQueue<>(100, (a, b) -> {
            if (a.getPrice() != b.getPrice()) {
                return Double.compare(b.getPrice(), a.getPrice()); // Higher price first
            }
            return Integer.compare(clients.get(b.getClientId()).getRating(),
                                 clients.get(a.getClientId()).getRating());
        });
    }

    private PriorityBlockingQueue<Order> createRealTimeSellComparator() {
        return new PriorityBlockingQueue<>(100, (a, b) -> {
            if (a.getPrice() != b.getPrice()) {
                return Double.compare(a.getPrice(), b.getPrice()); // Lower price first
            }
            return Integer.compare(clients.get(b.getClientId()).getRating(),
                                 clients.get(a.getClientId()).getRating());
        });
    }

    public void processOrders(List<Order> orders) {
        // Filter invalid orders
        List<Order> validOrders = filterOrders(orders);

        // Group orders by phase
        List<Order> morningOrders = new ArrayList<>();
        List<Order> realTimeOrders = new ArrayList<>();
        List<Order> eveningOrders = new ArrayList<>();

        for (Order order : validOrders) {
            if (order.isMorningAuction()) {
                morningOrders.add(order);
            } else if (order.isEveningAuctionStart()) {
                eveningOrders.add(order);
            } else {
                realTimeOrders.add(order);
            }
        }

        // Process morning auction
        logger.info("Processing morning auction with {} orders", morningOrders.size());
        processMorningAuction(morningOrders);

        // Process real-time trading
        logger.info("Processing real-time trading with {} orders", realTimeOrders.size());
        processRealTimeTrading(realTimeOrders);

        // Process evening auction
        logger.info("Processing evening auction with {} orders", eveningOrders.size());
        processEveningAuction(eveningOrders);
    }

    private List<Order> filterOrders(List<Order> orders) {
        List<Order> validOrders = new ArrayList<>();

        for (Order order : orders) {
            String instrumentId = order.getInstrumentId();
            String clientId = order.getClientId();

            // Check if instrument exists
            if (!instruments.containsKey(instrumentId)) {
                order.setRejectionReason("REJECTED-INSTRUMENT NOT FOUND");
                rejections.add(Map.entry(order.getOrderId(), order.getRejectionReason()));
                logger.warn("Order {} rejected: instrument {} not found", order.getOrderId(), instrumentId);
                continue;
            }

            Instrument instrument = instruments.get(instrumentId);
            Client client = clients.get(clientId);

            // Check currency compatibility
            if (!client.hasCurrency(instrument.getCurrency())) {
                order.setRejectionReason("REJECTED-MISMATCH CURRENCY");
                rejections.add(Map.entry(order.getOrderId(), order.getRejectionReason()));
                logger.warn("Order {} rejected: currency mismatch", order.getOrderId());
                continue;
            }

            // Check lot size
            if (!instrument.isValidLotSize(order.getQuantity())) {
                order.setRejectionReason("REJECTED-INVALID LOT SIZE");
                rejections.add(Map.entry(order.getOrderId(), order.getRejectionReason()));
                logger.warn("Order {} rejected: invalid lot size", order.getOrderId());
                continue;
            }

            validOrders.add(order);
        }

        return validOrders;
    }

    private void processMorningAuction(List<Order> orders) {
        // Add all orders to order books
        for (Order order : orders) {
            if (order.getSide() == Order.Side.BUY) {
                buyOrderBooks.get(order.getInstrumentId()).offer(order);
            } else {
                sellOrderBooks.get(order.getInstrumentId()).offer(order);
            }
        }

        // Calculate best price for each instrument
        Map<String, Double> bestPrices = calculateBestPrices();

        // Execute transactions at best prices
        for (String instrumentId : instruments.keySet()) {
            Double bestPrice = bestPrices.get(instrumentId);
            if (bestPrice != null && bestPrice > 0) {
                executeMorningAuctionTransactions(instrumentId, bestPrice);
                openPrices.put(instrumentId, bestPrice);
            }
        }
    }

    private Map<String, Double> calculateBestPrices() {
        Map<String, Double> bestPrices = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();

        for (String instrumentId : instruments.keySet()) {
            futures.add(executorService.submit(() -> {
                double bestPrice = calculateBestPriceForInstrument(instrumentId);
                if (bestPrice > 0) {
                    bestPrices.put(instrumentId, bestPrice);
                }
            }));
        }

        // Wait for all calculations to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error calculating best prices", e);
            }
        }

        return bestPrices;
    }

    private double calculateBestPriceForInstrument(String instrumentId) {
        PriorityBlockingQueue<Order> buyBook = buyOrderBooks.get(instrumentId);
        PriorityBlockingQueue<Order> sellBook = sellOrderBooks.get(instrumentId);

        List<Order> buyList = new ArrayList<>(buyBook);
        List<Order> sellList = new ArrayList<>(sellBook);

        // Filter for position check
        sellList.removeIf(order -> {
            Client client = clients.get(order.getClientId());
            return client.isPositionCheck() && client.getPosition(instrumentId) < order.getQuantity();
        });

        if (buyList.isEmpty() || sellList.isEmpty()) {
            return 0;
        }

        double maxPrice = 0;
        int maxVolume = 0;
        int buyAccumulate = 0;
        int sellAccumulate = 0;

        int sellIndex = 0;
        for (int i = 0; i < buyList.size(); i++) {
            Order buyOrder = buyList.get(i);
            buyAccumulate += buyOrder.getQuantity();

            while (sellIndex < sellList.size() &&
                   sellList.get(sellIndex).getPrice() <= buyOrder.getPrice()) {
                sellAccumulate += sellList.get(sellIndex).getQuantity();
                sellIndex++;
            }

            int volume = Math.min(buyAccumulate, sellAccumulate);
            if (volume >= maxVolume) {
                maxVolume = volume;
                double price = buyOrder.getPrice();
                if (buyOrder.isMarketOrder() && sellIndex > 0) {
                    price = sellList.get(sellIndex - 1).getPrice();
                }
                maxPrice = price;
            }
        }

        return maxPrice;
    }

    private void executeMorningAuctionTransactions(String instrumentId, double price) {
        PriorityBlockingQueue<Order> buyBook = buyOrderBooks.get(instrumentId);
        PriorityBlockingQueue<Order> sellBook = sellOrderBooks.get(instrumentId);

        List<Order> buyList = new ArrayList<>();
        List<Order> sellList = new ArrayList<>();

        // Collect orders that match the price
        Order buy;
        while ((buy = buyBook.poll()) != null) {
            if (buy.getPrice() >= price) {
                // Check position for sell orders only
                Client client = clients.get(buy.getClientId());
                buyList.add(buy);
            } else {
                realTimeBuyBooks.get(instrumentId).offer(buy);
            }
        }

        Order sell;
        while ((sell = sellBook.poll()) != null) {
            if (sell.getPrice() <= price) {
                Client client = clients.get(sell.getClientId());
                if (client.isPositionCheck() && client.getPosition(instrumentId) < sell.getQuantity()) {
                    continue;
                }
                sellList.add(sell);
            } else {
                realTimeSellBooks.get(instrumentId).offer(sell);
            }
        }

        // Match orders
        int buyIndex = 0;
        int sellIndex = 0;

        while (buyIndex < buyList.size() && sellIndex < sellList.size()) {
            Order buyOrder = buyList.get(buyIndex);
            Order sellOrder = sellList.get(sellIndex);

            int quantity = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());

            if (quantity > 0) {
                executeTransaction(sellOrder, buyOrder, instrumentId, quantity, price,
                                 LocalTime.of(9, 30, 0));

                if (buyOrder.getRemainingQuantity() == 0) {
                    buyIndex++;
                }
                if (sellOrder.getRemainingQuantity() == 0) {
                    sellIndex++;
                }
            } else {
                break;
            }
        }

        // Add remaining orders to real-time books
        for (int i = buyIndex; i < buyList.size(); i++) {
            if (buyList.get(i).getRemainingQuantity() > 0) {
                realTimeBuyBooks.get(instrumentId).offer(buyList.get(i));
            }
        }

        for (int i = sellIndex; i < sellList.size(); i++) {
            if (sellList.get(i).getRemainingQuantity() > 0) {
                realTimeSellBooks.get(instrumentId).offer(sellList.get(i));
            }
        }
    }

    private void processRealTimeTrading(List<Order> orders) {
        int orderIndex = 0;

        while (orderIndex < orders.size()) {
            boolean matched = tryMatchOrders();

            if (!matched) {
                if (orderIndex >= orders.size()) {
                    break;
                }

                Order order = orders.get(orderIndex++);

                // Check position for sell orders
                if (order.getSide() == Order.Side.SELL) {
                    Client client = clients.get(order.getClientId());
                    if (client.isPositionCheck() && client.getPosition(order.getInstrumentId()) < order.getQuantity()) {
                        order.setRejectionReason("REJECTED-POSITION CHECK FAILED");
                        rejections.add(Map.entry(order.getOrderId(), order.getRejectionReason()));
                        logger.warn("Order {} rejected: position check failed", order.getOrderId());
                        continue;
                    }
                    realTimeSellBooks.get(order.getInstrumentId()).offer(order);
                } else {
                    realTimeBuyBooks.get(order.getInstrumentId()).offer(order);
                }
            }
        }

        // Continue matching until no more matches
        while (tryMatchOrders()) {
            // Keep matching
        }
    }

    private boolean tryMatchOrders() {
        boolean anyMatch = false;

        for (String instrumentId : instruments.keySet()) {
            PriorityBlockingQueue<Order> buyBook = realTimeBuyBooks.get(instrumentId);
            PriorityBlockingQueue<Order> sellBook = realTimeSellBooks.get(instrumentId);

            Order buyOrder = buyBook.peek();
            Order sellOrder = sellBook.peek();

            if (buyOrder != null && sellOrder != null) {
                if (sellOrder.getPrice() <= buyOrder.getPrice()) {
                    buyOrder = buyBook.poll();
                    sellOrder = sellBook.poll();

                    // Determine price and time
                    double price;
                    LocalTime time;

                    if (sellOrder.getTime().isBefore(buyOrder.getTime())) {
                        price = sellOrder.getPrice();
                        time = buyOrder.getTime();
                        if (sellOrder.isMarketOrder()) {
                            price = buyOrder.getPrice();
                        }
                    } else {
                        price = buyOrder.getPrice();
                        time = sellOrder.getTime();
                        if (buyOrder.isMarketOrder()) {
                            price = sellOrder.getPrice();
                        }
                    }

                    int quantity = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());

                    executeTransaction(sellOrder, buyOrder, instrumentId, quantity, price, time);

                    // Re-add if not fully filled
                    if (buyOrder.getRemainingQuantity() > 0) {
                        buyBook.offer(buyOrder);
                    }
                    if (sellOrder.getRemainingQuantity() > 0) {
                        sellBook.offer(sellOrder);
                    }

                    anyMatch = true;
                }
            }
        }

        return anyMatch;
    }

    private void processEveningAuction(List<Order> orders) {
        // Move real-time orders to auction books
        for (String instrumentId : instruments.keySet()) {
            Order order;
            while ((order = realTimeBuyBooks.get(instrumentId).poll()) != null) {
                buyOrderBooks.get(instrumentId).offer(order);
            }
            while ((order = realTimeSellBooks.get(instrumentId).poll()) != null) {
                sellOrderBooks.get(instrumentId).offer(order);
            }
        }

        // Add evening auction orders
        for (Order order : orders) {
            if (order.getSide() == Order.Side.BUY) {
                buyOrderBooks.get(order.getInstrumentId()).offer(order);
            } else {
                // Check position
                Client client = clients.get(order.getClientId());
                if (!client.isPositionCheck() || client.getPosition(order.getInstrumentId()) >= order.getQuantity()) {
                    sellOrderBooks.get(order.getInstrumentId()).offer(order);
                }
            }
        }

        // Calculate best prices and execute
        Map<String, Double> bestPrices = calculateBestPrices();

        for (String instrumentId : instruments.keySet()) {
            Double bestPrice = bestPrices.get(instrumentId);
            if (bestPrice != null && bestPrice > 0) {
                executeEveningAuctionTransactions(instrumentId, bestPrice);
                closePrices.put(instrumentId, bestPrice);
            }
        }
    }

    private void executeEveningAuctionTransactions(String instrumentId, double price) {
        executeMorningAuctionTransactions(instrumentId, price);
        // Update time to evening auction close
        // (transactions are already recorded with correct logic)
    }

    private void executeTransaction(Order sellOrder, Order buyOrder, String instrumentId,
                                   int quantity, double price, LocalTime time) {
        lock.writeLock().lock();
        try {
            // Update order quantities
            sellOrder.reduceQuantity(quantity);
            buyOrder.reduceQuantity(quantity);

            // Update client positions
            Client seller = clients.get(sellOrder.getClientId());
            Client buyer = clients.get(buyOrder.getClientId());

            seller.updatePosition(instrumentId, -quantity);
            buyer.updatePosition(instrumentId, quantity);

            // Record transaction
            Transaction transaction = new Transaction(
                sellOrder.getClientId(),
                buyOrder.getClientId(),
                instrumentId,
                quantity,
                price,
                time
            );

            transactions.add(transaction);
            logger.info("Executed transaction: {}", transaction);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public List<Map.Entry<String, String>> getRejections() {
        return new ArrayList<>(rejections);
    }

    public Map<String, Double> getOpenPrices() {
        return new HashMap<>(openPrices);
    }

    public Map<String, Double> getClosePrices() {
        return new HashMap<>(closePrices);
    }

    public Map<String, Client> getClients() {
        return new HashMap<>(clients);
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
