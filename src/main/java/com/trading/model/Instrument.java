package com.trading.model;

/**
 * Represents a tradable instrument with its currency and lot size requirements
 */
public class Instrument {
    private final String instrumentId;
    private final String currency;
    private final int lotSize;

    public Instrument(String instrumentId, String currency, int lotSize) {
        this.instrumentId = instrumentId;
        this.currency = currency;
        this.lotSize = lotSize;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public String getCurrency() {
        return currency;
    }

    public int getLotSize() {
        return lotSize;
    }

    public boolean isValidLotSize(int quantity) {
        return quantity % lotSize == 0;
    }

    @Override
    public String toString() {
        return "Instrument{" +
                "instrumentId='" + instrumentId + '\'' +
                ", currency='" + currency + '\'' +
                ", lotSize=" + lotSize +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instrument that = (Instrument) o;
        return instrumentId.equals(that.instrumentId);
    }

    @Override
    public int hashCode() {
        return instrumentId.hashCode();
    }
}
