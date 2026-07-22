package com.trading.fix;

import com.trading.engine.OrderMatchingEngine;
import com.trading.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles incoming FIX messages and converts them to internal Order objects
 */
public class FIXMessageHandler extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(FIXMessageHandler.class);
    private static final AtomicLong EXECUTION_SEQUENCE = new AtomicLong();

    private final OrderMatchingEngine engine;
    private final List<Order> pendingOrders;

    public FIXMessageHandler(OrderMatchingEngine engine) {
        this.engine = engine;
        this.pendingOrders = new ArrayList<>();
    }

    @Override
    public void onCreate(SessionID sessionID) {
        logger.info("FIX session created: {}", sessionID);
    }

    @Override
    public void onLogon(SessionID sessionID) {
        logger.info("FIX session logged on: {}", sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        logger.info("FIX session logged out: {}", sessionID);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        logger.debug("Sending admin message: {}", message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        logger.debug("Received admin message: {}", message);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        logger.debug("Sending app message: {}", message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        logger.info("Received app message: {}", message);
        crack(message, sessionID);
    }

    /**
     * Handles New Order Single messages (FIX tag 35=D)
     */
    public void onMessage(NewOrderSingle message, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {

        String clOrdID = message.getClOrdID().getValue();
        String symbol = message.getSymbol().getValue();
        char fixSide = message.getSide().getValue();
        double orderQty = message.getOrderQty().getValue();

        final Order order;
        try {
            order = convertOrder(message, LocalTime.now());
        } catch (IllegalArgumentException e) {
            logger.warn("Rejected FIX order {}: {}", clOrdID, e.getMessage());
            sendExecutionReport(
                sessionID, clOrdID, symbol, fixSide, ExecType.REJECTED,
                0, 0, 0, e.getMessage()
            );
            return;
        }

        logger.info("Created order from FIX message: {}", order);

        // Add to pending orders for batch processing
        synchronized (pendingOrders) {
            pendingOrders.add(order);
        }

        // Send acknowledgment
        sendExecutionReport(
            sessionID, clOrdID, symbol, fixSide, ExecType.NEW,
            orderQty, 0, 0, "Order accepted"
        );
    }

    Order convertOrder(NewOrderSingle message, LocalTime time) throws FieldNotFound {
        String clOrdID = requireText(message.getClOrdID().getValue(), "ClOrdID is required");
        String symbol = requireText(message.getSymbol().getValue(), "Symbol is required");
        if (!message.isSetField(Account.FIELD)) {
            throw new IllegalArgumentException("Account is required");
        }
        String clientId = requireText(message.getAccount().getValue(), "Account is required");

        char fixSide = message.getSide().getValue();
        Order.Side orderSide;
        if (fixSide == Side.BUY) {
            orderSide = Order.Side.BUY;
        } else if (fixSide == Side.SELL) {
            orderSide = Order.Side.SELL;
        } else {
            throw new IllegalArgumentException("Side must be BUY or SELL");
        }

        double rawQuantity = message.getOrderQty().getValue();
        if (!Double.isFinite(rawQuantity)
            || rawQuantity <= 0
            || rawQuantity != Math.rint(rawQuantity)
            || rawQuantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("OrderQty must be a positive whole number");
        }
        int quantity = (int) rawQuantity;

        char orderType = message.getOrdType().getValue();
        if (orderType == OrdType.MARKET) {
            return Order.market(clOrdID, clientId, symbol, quantity, orderSide, time);
        }
        if (orderType == OrdType.LIMIT) {
            if (!message.isSetField(Price.FIELD)) {
                throw new IllegalArgumentException("Price is required for limit orders");
            }
            BigDecimal price = BigDecimal.valueOf(message.getPrice().getValue());
            if (price.signum() <= 0) {
                throw new IllegalArgumentException("Price must be greater than zero");
            }
            return Order.limit(clOrdID, clientId, symbol, quantity, price, orderSide, time);
        }
        throw new IllegalArgumentException("Unsupported order type");
    }

    private String requireText(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    /**
     * Sends an execution report back to the client
     */
    private void sendExecutionReport(
        SessionID sessionID,
        String clOrdID,
        String symbol,
        char side,
        char execType,
        double leavesQty,
        double cumQty,
        double avgPx,
        String text
    ) {
        try {
            ExecutionReport report = createExecutionReport(
                clOrdID, symbol, side, execType, leavesQty, cumQty, avgPx, text
            );
            Session.sendToTarget(report, sessionID);
            logger.info("Sent execution report: {}", report);
        } catch (SessionNotFound e) {
            logger.error("Session not found when sending execution report", e);
        }
    }

    ExecutionReport createExecutionReport(
        String clOrdID,
        String symbol,
        char side,
        char execType,
        double leavesQty,
        double cumQty,
        double avgPx,
        String text
    ) {
        ExecutionReport report = new ExecutionReport(
            new OrderID(clOrdID),
            new ExecID(generateExecID()),
            new ExecType(execType),
            new OrdStatus(getOrdStatus(execType)),
            new Side(side),
            new LeavesQty(leavesQty),
            new CumQty(cumQty),
            new AvgPx(avgPx)
        );
        report.set(new ClOrdID(clOrdID));
        report.set(new Symbol(symbol));
        report.set(new Text(text));
        return report;
    }

    private char getOrdStatus(char execType) {
        switch (execType) {
            case ExecType.NEW:
                return OrdStatus.NEW;
            case ExecType.REJECTED:
                return OrdStatus.REJECTED;
            case ExecType.FILL:
                return OrdStatus.FILLED;
            case ExecType.PARTIAL_FILL:
                return OrdStatus.PARTIALLY_FILLED;
            default:
                return OrdStatus.NEW;
        }
    }

    private String generateExecID() {
        return "LIMITFORGE-" + EXECUTION_SEQUENCE.incrementAndGet();
    }

    /**
     * Returns pending orders and clears the list
     */
    public List<Order> getPendingOrders() {
        synchronized (pendingOrders) {
            List<Order> orders = new ArrayList<>(pendingOrders);
            pendingOrders.clear();
            return orders;
        }
    }
}
