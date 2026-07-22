package com.trading.fix;

import com.trading.engine.OrderMatchingEngine;
import com.trading.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles incoming FIX messages and converts them to internal Order objects
 */
public class FIXMessageHandler extends MessageCracker implements Application {
    private static final Logger logger = LoggerFactory.getLogger(FIXMessageHandler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

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
        Side side = message.getSide();
        double orderQty = message.getOrderQty().getValue();
        OrdType ordType = message.getOrdType();

        boolean isMarketOrder;
        double price = 0;
        if (ordType.getValue() == OrdType.MARKET) {
            isMarketOrder = true;
        } else if (ordType.getValue() == OrdType.LIMIT) {
            isMarketOrder = false;
            price = message.getPrice().getValue();
        } else {
            logger.warn("Unsupported order type: {}", ordType.getValue());
            sendExecutionReport(sessionID, clOrdID, symbol, ExecType.REJECTED, "Unsupported order type");
            return;
        }

        // Extract client ID from message (could be in Account field or other custom field)
        String clientId = "UNKNOWN";
        if (message.isSetField(Account.FIELD)) {
            clientId = message.getAccount().getValue();
        }

        Order.Side orderSide = (side.getValue() == Side.BUY) ? Order.Side.BUY : Order.Side.SELL;
        LocalTime time = LocalTime.now();

        Order order = isMarketOrder
            ? Order.market(clOrdID, clientId, symbol, (int) orderQty, orderSide, time)
            : Order.limit(clOrdID, clientId, symbol, (int) orderQty, price, orderSide, time);

        logger.info("Created order from FIX message: {}", order);

        // Add to pending orders for batch processing
        synchronized (pendingOrders) {
            pendingOrders.add(order);
        }

        // Send acknowledgment
        sendExecutionReport(sessionID, clOrdID, symbol, ExecType.NEW, "Order accepted");
    }

    /**
     * Sends an execution report back to the client
     */
    private void sendExecutionReport(SessionID sessionID, String clOrdID, String symbol,
                                     char execType, String text) {
        try {
            ExecutionReport report = new ExecutionReport(
                new OrderID(clOrdID),
                new ExecID(generateExecID()),
                new ExecType(execType),
                new OrdStatus(getOrdStatus(execType)),
                new Side(Side.BUY),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0)
            );

            report.set(new ClOrdID(clOrdID));
            report.set(new Symbol(symbol));
            report.set(new Text(text));

            Session.sendToTarget(report, sessionID);
            logger.info("Sent execution report: {}", report);
        } catch (SessionNotFound e) {
            logger.error("Session not found when sending execution report", e);
        }
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
        return "EXEC-" + System.currentTimeMillis();
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
