package com.trading.fix;

import com.trading.engine.OrderMatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * FIX protocol server that accepts connections and processes order messages
 */
public class FIXServer {
    private static final Logger logger = LoggerFactory.getLogger(FIXServer.class);

    private final SocketAcceptor acceptor;
    private final FIXMessageHandler messageHandler;

    public FIXServer(String configFile, OrderMatchingEngine engine) throws ConfigError {
        SessionSettings settings;

        try (InputStream inputStream = new FileInputStream(configFile)) {
            settings = new SessionSettings(inputStream);
        } catch (Exception e) {
            logger.error("Failed to load FIX configuration from {}", configFile, e);
            throw new ConfigError("Failed to load FIX configuration: " + e.getMessage());
        }

        this.messageHandler = new FIXMessageHandler(engine);
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        this.acceptor = new SocketAcceptor(
            messageHandler,
            storeFactory,
            settings,
            logFactory,
            messageFactory
        );

        logger.info("FIX server initialized with config: {}", configFile);
    }

    public void start() throws ConfigError, RuntimeError {
        acceptor.start();
        logger.info("FIX server started and accepting connections");
    }

    public void stop() {
        acceptor.stop();
        logger.info("FIX server stopped");
    }

    public FIXMessageHandler getMessageHandler() {
        return messageHandler;
    }

    public boolean isLoggedOn() {
        return acceptor.getSessions().stream()
            .anyMatch(sessionID -> Session.lookupSession(sessionID).isLoggedOn());
    }
}
