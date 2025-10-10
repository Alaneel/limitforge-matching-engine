package com.trading.model;

import java.time.LocalTime;

/**
 * Represents a completed transaction between two parties
 */
public class Transaction {
    private final String fromClientId;
    private final String toClientId;
    private final String instrumentId;
    private final int quantity;
    private final double price;
    private final LocalTime time;

    public Transaction(String fromClientId, String toClientId, String instrumentId,
                      int quantity, double price, LocalTime time) {
        this.fromClientId = fromClientId;
        this.toClientId = toClientId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.price = price;
        this.time = time;
    }

    public String getFromClientId() {
        return fromClientId;
    }

    public String getToClientId() {
        return toClientId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public LocalTime getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "from='" + fromClientId + '\'' +
                ", to='" + toClientId + '\'' +
                ", instrument='" + instrumentId + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", time=" + time +
                '}';
    }
}
