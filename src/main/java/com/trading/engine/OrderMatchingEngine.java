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
        return new PriorityBlockingQueue<>(100, buyOrderComparator());
    }

    private PriorityBlockingQueue<Order> createSellComparator() {
        return new PriorityBlockingQueue<>(100, sellOrderComparator());
    }

    private PriorityBlockingQueue<Order> createRealTimeBuyComparator() {
        return new PriorityBlockingQueue<>(100, buyOrderComparator());
    }

    private PriorityBlockingQueue<Order> createRealTimeSellComparator() {
        return new PriorityBlockingQueue<>(100, sellOrderComparator());
    }

    private Comparator<Order> buyOrderComparator() {
        return (a, b) -> {
            if (a.isMarketOrder() != b.isMarketOrder()) {
                return a.isMarketOrder() ? -1 : 1;
            }
            int priceComparison = Double.compare(b.getPrice(), a.getPrice());
            return priceComparison != 0 ? priceComparison : compareByTimeThenId(a, b);
        };
    }

    private Comparator<Order> sellOrderComparator() {
        return (a, b) -> {
            if (a.isMarketOrder() != b.isMarketOrder()) {
                return a.isMarketOrder() ? -1 : 1;
            }
            int priceComparison = Double.compare(a.getPrice(), b.getPrice());
            return priceComparison != 0 ? priceComparison : compareByTimeThenId(a, b);
        };
    }

    private int compareByTimeThenId(Order a, Order b) {
        int timeComparison = a.getTime().compareTo(b.getTime());
        return timeComparison != 0
            ? timeComparison
            : a.getOrderId().compareTo(b.getOrderId());
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

            // Check if client exists before applying client-specific rules
            if (client == null) {
                order.setRejectionReason("REJECTED-CLIENT NOT FOUND");
                rejections.add(Map.entry(order.getOrderId(), order.getRejectionReason()));
                logger.warn("Order {} rejected: client {} not found", order.getOrderId(), clientId);
                continue;
            }

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
                executeAuctionTransactions(
                    instrumentId,
                    bestPrice,
                    LocalTime.of(9, 30),
                    true
                );
                openPrices.put(instrumentId, bestPrice);
            } else {
                moveAuctionBooksToContinuousTrading(instrumentId);
            }
        }
    }

    private void moveAuctionBooksToContinuousTrading(String instrumentId) {
        Order order;
        while ((order = buyOrderBooks.get(instrumentId).poll()) != null) {
            realTimeBuyBooks.get(instrumentId).offer(order);
        }
        while ((order = sellOrderBooks.get(instrumentId).poll()) != null) {
            realTimeSellBooks.get(instrumentId).offer(order);
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

        buyList.sort(buyOrderComparator());
        sellList.sort(sellOrderComparator());

        // Filter for position check
        sellList.removeIf(order -> {
            Client client = clients.get(order.getClientId());
            return client.isPositionCheck()
                && client.getPosition(instrumentId) < order.getRemainingQuantity();
        });

        if (buyList.isEmpty() || sellList.isEmpty()) {
            return 0;
        }

        SortedSet<Double> candidatePrices = new TreeSet<>();
        buyList.stream()
            .filter(order -> !order.isMarketOrder())
            .map(Order::getPrice)
            .forEach(candidatePrices::add);
        sellList.stream()
            .filter(order -> !order.isMarketOrder())
            .map(Order::getPrice)
            .forEach(candidatePrices::add);

        if (candidatePrices.isEmpty()) {
            return 0;
        }

        double referencePrice = referencePriceFor(instrumentId);
        ClearingCandidate best = null;
        for (double candidatePrice : candidatePrices) {
            long buyVolume = executableBuyVolume(buyList, candidatePrice);
            long sellVolume = executableSellVolume(sellList, candidatePrice);
            ClearingCandidate candidate = new ClearingCandidate(
                candidatePrice,
                Math.min(buyVolume, sellVolume),
                Math.abs(buyVolume - sellVolume),
                referencePrice > 0 ? Math.abs(candidatePrice - referencePrice) : 0
            );
            if (candidate.isBetterThan(best)) {
                best = candidate;
            }
        }

        return best != null && best.volume() > 0 ? best.price() : 0;
    }

    private long executableBuyVolume(List<Order> orders, double price) {
        return orders.stream()
            .filter(order -> order.isMarketOrder() || order.getPrice() >= price)
            .mapToLong(Order::getRemainingQuantity)
            .sum();
    }

    private long executableSellVolume(List<Order> orders, double price) {
        return orders.stream()
            .filter(order -> order.isMarketOrder() || order.getPrice() <= price)
            .mapToLong(Order::getRemainingQuantity)
            .sum();
    }

    private double referencePriceFor(String instrumentId) {
        for (int index = transactions.size() - 1; index >= 0; index--) {
            Transaction transaction = transactions.get(index);
            if (transaction.getInstrumentId().equals(instrumentId)) {
                return transaction.getPrice();
            }
        }
        return openPrices.getOrDefault(instrumentId, 0.0);
    }

    private void executeAuctionTransactions(
        String instrumentId,
        double price,
        LocalTime executionTime,
        boolean carryRemainderToContinuousTrading
    ) {
        PriorityBlockingQueue<Order> buyBook = buyOrderBooks.get(instrumentId);
        PriorityBlockingQueue<Order> sellBook = sellOrderBooks.get(instrumentId);

        List<Order> buyList = new ArrayList<>();
        List<Order> sellList = new ArrayList<>();

        // Collect orders that match the price
        Order buy;
        while ((buy = buyBook.poll()) != null) {
            if (buy.isMarketOrder() || buy.getPrice() >= price) {
                buyList.add(buy);
            } else if (carryRemainderToContinuousTrading) {
                realTimeBuyBooks.get(instrumentId).offer(buy);
            }
        }

        Order sell;
        while ((sell = sellBook.poll()) != null) {
            if (sell.isMarketOrder() || sell.getPrice() <= price) {
                Client client = clients.get(sell.getClientId());
                if (client.isPositionCheck()
                    && client.getPosition(instrumentId) < sell.getRemainingQuantity()) {
                    continue;
                }
                sellList.add(sell);
            } else if (carryRemainderToContinuousTrading) {
                realTimeSellBooks.get(instrumentId).offer(sell);
            }
        }

        buyList.sort(buyOrderComparator());
        sellList.sort(sellOrderComparator());

        // Match orders
        int buyIndex = 0;
        int sellIndex = 0;

        while (buyIndex < buyList.size() && sellIndex < sellList.size()) {
            Order buyOrder = buyList.get(buyIndex);
            Order sellOrder = sellList.get(sellIndex);

            int quantity = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());

            if (quantity > 0) {
                executeTransaction(sellOrder, buyOrder, instrumentId, quantity, price,
                                 executionTime);

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

        if (carryRemainderToContinuousTrading) {
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
                if (canMatch(buyOrder, sellOrder)) {
                    buyOrder = buyBook.poll();
                    sellOrder = sellBook.poll();

                    // Determine price and time
                    double price;
                    LocalTime time;

                    if (sellOrder.isMarketOrder() && buyOrder.isMarketOrder()) {
                        // There is no reference price for two opposing market orders.
                        buyBook.offer(buyOrder);
                        sellBook.offer(sellOrder);
                        continue;
                    } else if (sellOrder.isMarketOrder()) {
                        price = buyOrder.getPrice();
                        time = laterTime(buyOrder, sellOrder);
                    } else if (buyOrder.isMarketOrder()) {
                        price = sellOrder.getPrice();
                        time = laterTime(buyOrder, sellOrder);
                    } else if (sellOrder.getTime().isBefore(buyOrder.getTime())) {
                        price = sellOrder.getPrice();
                        time = buyOrder.getTime();
                    } else {
                        price = buyOrder.getPrice();
                        time = sellOrder.getTime();
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

    private boolean canMatch(Order buyOrder, Order sellOrder) {
        return buyOrder.isMarketOrder()
            || sellOrder.isMarketOrder()
            || sellOrder.getPrice() <= buyOrder.getPrice();
    }

    private LocalTime laterTime(Order first, Order second) {
        return first.getTime().isAfter(second.getTime()) ? first.getTime() : second.getTime();
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
        executeAuctionTransactions(instrumentId, price, LocalTime.of(16, 10), false);
    }

    private record ClearingCandidate(
        double price,
        long volume,
        long imbalance,
        double referenceDistance
    ) {
        private boolean isBetterThan(ClearingCandidate other) {
            if (other == null) {
                return true;
            }
            if (volume != other.volume) {
                return volume > other.volume;
            }
            if (imbalance != other.imbalance) {
                return imbalance < other.imbalance;
            }
            int distanceComparison = Double.compare(referenceDistance, other.referenceDistance);
            if (distanceComparison != 0) {
                return distanceComparison < 0;
            }
            return price > other.price;
        }
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
