package com.trading.fix;

import com.trading.model.Order;
import org.junit.jupiter.api.Test;
import quickfix.FieldNotFound;
import quickfix.field.Account;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecType;
import quickfix.field.LeavesQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.TransactTime;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FIXMessageHandlerTest {
    private final FIXMessageHandler handler = new FIXMessageHandler(null);

    @Test
    void convertsValidLimitOrder() throws FieldNotFound {
        NewOrderSingle message = limitOrder(100, 10.25);

        Order order = handler.convertOrder(message, LocalTime.NOON);

        assertEquals("FIX-1", order.getOrderId());
        assertEquals("CLIENT-A", order.getClientId());
        assertEquals(Order.Side.BUY, order.getSide());
        assertEquals(Order.Type.LIMIT, order.getType());
        assertEquals(new BigDecimal("10.25"), order.getPrice());
    }

    @Test
    void rejectsFractionalQuantity() {
        NewOrderSingle message = limitOrder(100.5, 10.25);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> handler.convertOrder(message, LocalTime.NOON)
        );

        assertEquals("OrderQty must be a positive whole number", exception.getMessage());
    }

    @Test
    void rejectsMissingAccount() {
        NewOrderSingle message = limitOrder(100, 10.25);
        message.removeField(Account.FIELD);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> handler.convertOrder(message, LocalTime.NOON)
        );

        assertEquals("Account is required", exception.getMessage());
    }

    @Test
    void createsAccurateNewExecutionReport() throws FieldNotFound {
        ExecutionReport report = handler.createExecutionReport(
            "FIX-1", "SIA", Side.SELL, ExecType.NEW, 100, 0, 0, "Order accepted"
        );

        assertEquals(Side.SELL, report.getSide().getValue());
        assertEquals(OrdStatus.NEW, report.getOrdStatus().getValue());
        assertEquals(100, report.getDouble(LeavesQty.FIELD));
        assertEquals(0, report.getDouble(CumQty.FIELD));
        assertEquals(0, report.getDouble(AvgPx.FIELD));
    }

    @Test
    void generatesUniqueExecutionIds() throws FieldNotFound {
        ExecutionReport first = handler.createExecutionReport(
            "FIX-1", "SIA", Side.BUY, ExecType.NEW, 100, 0, 0, "accepted"
        );
        ExecutionReport second = handler.createExecutionReport(
            "FIX-2", "SIA", Side.BUY, ExecType.NEW, 100, 0, 0, "accepted"
        );

        assertNotEquals(first.getString(ExecID.FIELD), second.getString(ExecID.FIELD));
    }

    private NewOrderSingle limitOrder(double quantity, double price) {
        NewOrderSingle message = new NewOrderSingle(
            new ClOrdID("FIX-1"),
            new Side(Side.BUY),
            new TransactTime(),
            new OrdType(OrdType.LIMIT)
        );
        message.set(new Symbol("SIA"));
        message.set(new OrderQty(quantity));
        message.set(new Price(price));
        message.set(new Account("CLIENT-A"));
        return message;
    }
}
