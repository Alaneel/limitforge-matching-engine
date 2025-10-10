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
    private final Side side;
    private final LocalTime time;
    private volatile boolean valid;
    private volatile String rejectionReason;

    public enum Side {
        BUY, SELL
    }

    public Order(String orderId, String clientId, String instrumentId,
                 int quantity, double price, Side side, LocalTime time) {
        this.orderId = orderId;
        this.clientId = clientId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.remainingQuantity = new AtomicInteger(quantity);
        this.price = price;
        this.side = side;
        this.time = time;
        this.valid = true;
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
        return true;
    }

    public double getPrice() {
        return price;
    }

    public boolean isMarketOrder() {
        return price == Double.MAX_VALUE;
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

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
        this.valid = false;
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
                ", valid=" + valid +
                '}';
    }
}
