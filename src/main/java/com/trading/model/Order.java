package com.trading.model;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a trading order with all necessary information
 */
public class Order {
    private final String orderId;
    private final String clientId;
    private final String instrumentId;
    private final int quantity;
    private final AtomicInteger remainingQuantity;
    private final double price;
    private final Type type;
    private final Side side;
    private final LocalTime time;
    private volatile boolean valid;
    private volatile Status status;
    private volatile String rejectionReason;

    public enum Side {
        BUY, SELL
    }

    public enum Type {
        MARKET, LIMIT
    }

    public enum Status {
        NEW, PARTIALLY_FILLED, FILLED, REJECTED
    }

    private Order(String orderId, String clientId, String instrumentId,
                  int quantity, double price, Type type, Side side, LocalTime time) {
        this.orderId = orderId;
        this.clientId = clientId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.remainingQuantity = new AtomicInteger(quantity);
        this.price = price;
        this.type = type;
        this.side = side;
        this.time = time;
        this.valid = true;
        this.status = Status.NEW;
    }

    public static Order market(String orderId, String clientId, String instrumentId,
                               int quantity, Side side, LocalTime time) {
        return new Order(orderId, clientId, instrumentId, quantity, 0, Type.MARKET, side, time);
    }

    public static Order limit(String orderId, String clientId, String instrumentId,
                              int quantity, double price, Side side, LocalTime time) {
        return new Order(orderId, clientId, instrumentId, quantity, price, Type.LIMIT, side, time);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity.get();
    }

    public boolean reduceQuantity(int amount) {
        int current;
        int newValue;
        do {
            current = remainingQuantity.get();
            if (current < amount) {
                return false;
            }
            newValue = current - amount;
        } while (!remainingQuantity.compareAndSet(current, newValue));
        status = newValue == 0 ? Status.FILLED : Status.PARTIALLY_FILLED;
        return true;
    }

    public double getPrice() {
        return price;
    }

    public boolean isMarketOrder() {
        return type == Type.MARKET;
    }

    public Type getType() {
        return type;
    }

    public Side getSide() {
        return side;
    }

    public LocalTime getTime() {
        return time;
    }

    public boolean isValid() {
        return valid;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
        this.valid = false;
        this.status = Status.REJECTED;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isMorningAuction() {
        return time.isBefore(LocalTime.of(9, 30));
    }

    public boolean isEveningAuctionStart() {
        return !time.isBefore(LocalTime.of(16, 0)) && time.isBefore(LocalTime.of(16, 10));
    }

    public boolean isEveningAuctionEnd() {
        return !time.isBefore(LocalTime.of(16, 10));
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", instrumentId='" + instrumentId + '\'' +
                ", quantity=" + quantity +
                ", remainingQuantity=" + remainingQuantity.get() +
                ", price=" + (isMarketOrder() ? "Market" : price) +
                ", side=" + side +
                ", time=" + time +
                ", status=" + status +
                ", valid=" + valid +
                '}';
    }
}
