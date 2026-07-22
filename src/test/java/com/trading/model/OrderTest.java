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
        assertEquals(Order.Status.NEW, order.getStatus());
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

    @Test
    void tracksPartialAndCompleteFills() {
        Order order = Order.limit(
            "L1", "CLIENT", "SIA", 200, 10.5, Order.Side.SELL, LocalTime.NOON
        );

        assertTrue(order.reduceQuantity(100));
        assertEquals(Order.Status.PARTIALLY_FILLED, order.getStatus());
        assertTrue(order.reduceQuantity(100));
        assertEquals(Order.Status.FILLED, order.getStatus());
    }

    @Test
    void tracksRejectionState() {
        Order order = Order.market(
            "M1", "CLIENT", "SIA", 100, Order.Side.BUY, LocalTime.NOON
        );

        order.setRejectionReason("test rejection");

        assertEquals(Order.Status.REJECTED, order.getStatus());
        assertFalse(order.isValid());
    }
}
