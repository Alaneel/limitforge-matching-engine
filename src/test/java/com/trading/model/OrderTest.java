package com.trading.model;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTest {
    @Test
    void createsExplicitMarketOrder() {
        Order order = Order.market(
            "M1", "CLIENT", "SIA", 100, Order.Side.BUY, LocalTime.NOON
        );

        assertTrue(order.isMarketOrder());
        assertEquals(Order.Type.MARKET, order.getType());
    }

    @Test
    void createsExplicitLimitOrder() {
        Order order = Order.limit(
            "L1", "CLIENT", "SIA", 100, 10.5, Order.Side.SELL, LocalTime.NOON
        );

        assertFalse(order.isMarketOrder());
        assertEquals(Order.Type.LIMIT, order.getType());
        assertEquals(10.5, order.getPrice());
    }
}
