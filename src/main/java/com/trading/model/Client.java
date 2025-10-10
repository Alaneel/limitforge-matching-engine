package com.trading.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a trading client with their currencies, position checking requirements, and holdings
 */
public class Client {
    private final String clientId;
    private final Set<String> currencies;
    private final boolean positionCheck;
    private final int rating;
    private final Map<String, Integer> positions;

    public Client(String clientId, Set<String> currencies, boolean positionCheck, int rating) {
        this.clientId = clientId;
        this.currencies = Collections.unmodifiableSet(currencies);
        this.positionCheck = positionCheck;
        this.rating = rating;
        this.positions = new ConcurrentHashMap<>();
    }

    public String getClientId() {
        return clientId;
    }

    public Set<String> getCurrencies() {
        return currencies;
    }

    public boolean isPositionCheck() {
        return positionCheck;
    }

    public int getRating() {
        return rating;
    }

    public Map<String, Integer> getPositions() {
        return positions;
    }

    public int getPosition(String instrument) {
        return positions.getOrDefault(instrument, 0);
    }

    public synchronized void updatePosition(String instrument, int delta) {
        positions.merge(instrument, delta, Integer::sum);
    }

    public boolean hasCurrency(String currency) {
        return currencies.contains(currency);
    }

    @Override
    public String toString() {
        return "Client{" +
                "clientId='" + clientId + '\'' +
                ", currencies=" + currencies +
                ", positionCheck=" + positionCheck +
                ", rating=" + rating +
                ", positions=" + positions +
                '}';
    }
}
