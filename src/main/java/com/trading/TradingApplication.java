package com.trading;

import com.trading.csv.CSVReader;
import com.trading.csv.CSVWriter;
import com.trading.engine.OrderMatchingEngine;
import com.trading.fix.FIXServer;
import com.trading.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main application class for the trading system
 * Supports both CSV file processing and FIX protocol communication
 */
public class TradingApplication {
    private static final Logger logger = LoggerFactory.getLogger(TradingApplication.class);

    private static final String CLIENTS_FILE = "input_clients.csv";
    private static final String INSTRUMENTS_FILE = "input_instruments.csv";
    private static final String ORDERS_FILE = "input_orders.csv";

    private static final String EXCHANGE_REPORT_FILE = "output_exchange_report.csv";
    private static final String CLIENT_REPORT_FILE = "output_client_report.csv";
    private static final String INSTRUMENT_REPORT_FILE = "output_instrument_report.csv";

    private static final String FIX_CONFIG_FILE = "src/main/resources/fix-server.cfg";

    public static void main(String[] args) {
        logger.info("Starting LimitForge Matching Engine");

        try {
            // Load data
            logger.info("Loading trading data from CSV files...");
            List<Client> clientList = CSVReader.readClients(CLIENTS_FILE);
            List<Instrument> instrumentList = CSVReader.readInstruments(INSTRUMENTS_FILE);
            List<Order> orderList = CSVReader.readOrders(ORDERS_FILE);

            // Create lookup maps
            Map<String, Client> clients = clientList.stream()
                .collect(Collectors.toMap(Client::getClientId, c -> c));

            Map<String, Instrument> instruments = instrumentList.stream()
                .collect(Collectors.toMap(Instrument::getInstrumentId, i -> i));

            logger.info("Loaded {} clients, {} instruments, {} orders",
                       clientList.size(), instrumentList.size(), orderList.size());

            // Initialize order matching engine
            OrderMatchingEngine engine = new OrderMatchingEngine(clients, instruments);

            // Check if FIX server should be enabled
            boolean useFIXServer = shouldUseFIXServer();

            FIXServer fixServer = null;
            if (useFIXServer) {
                logger.info("Starting FIX protocol server...");
                try {
                    fixServer = new FIXServer(FIX_CONFIG_FILE, engine);
                    fixServer.start();
                    logger.info("FIX server is running and ready to accept connections");

                    // Wait for FIX orders (with timeout)
                    waitForFIXOrders(fixServer, engine);
                } catch (Exception e) {
                    logger.warn("FIX server could not be started: {}. Continuing with CSV mode only.", e.getMessage());
                    useFIXServer = false;
                }
            }

            // Process CSV orders
            logger.info("Processing orders from CSV file...");
            engine.processOrders(orderList);

            // Process any pending FIX orders
            if (useFIXServer && fixServer != null) {
                List<Order> fixOrders = fixServer.getMessageHandler().getPendingOrders();
                if (!fixOrders.isEmpty()) {
                    logger.info("Processing {} pending FIX orders", fixOrders.size());
                    engine.processOrders(fixOrders);
                }
            }

            // Generate reports
            logger.info("Generating reports...");
            generateReports(engine, instruments);

            // Shutdown
            if (fixServer != null) {
                fixServer.stop();
            }
            engine.shutdown();

            logger.info("Trading system completed successfully");
            System.out.println("\n=== Trading System Summary ===");
            System.out.println("Total transactions: " + engine.getTransactions().size());
            System.out.println("Total rejections: " + engine.getRejections().size());
            System.out.println("Reports generated:");
            System.out.println("  - " + EXCHANGE_REPORT_FILE);
            System.out.println("  - " + CLIENT_REPORT_FILE);
            System.out.println("  - " + INSTRUMENT_REPORT_FILE);

        } catch (Exception e) {
            logger.error("Fatal error in trading system", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean shouldUseFIXServer() {
        File fixConfigFile = new File(FIX_CONFIG_FILE);
        String enableFIX = System.getProperty("fix.enabled", "false");
        return fixConfigFile.exists() && "true".equalsIgnoreCase(enableFIX);
    }

    private static void waitForFIXOrders(FIXServer fixServer, OrderMatchingEngine engine) {
        int maxWaitSeconds = 30;
        int waitedSeconds = 0;

        logger.info("Waiting up to {} seconds for FIX connections...", maxWaitSeconds);

        while (waitedSeconds < maxWaitSeconds) {
            if (fixServer.isLoggedOn()) {
                logger.info("FIX client connected");
                // Wait a bit more for orders to come in
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                return;
            }

            try {
                Thread.sleep(1000);
                waitedSeconds++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("No FIX connections received within timeout period");
    }

    private static void generateReports(OrderMatchingEngine engine,
                                       Map<String, Instrument> instruments) throws Exception {
        // Exchange report (rejections)
        CSVWriter.writeExchangeReport(EXCHANGE_REPORT_FILE, engine.getRejections());

        // Client report (positions)
        CSVWriter.writeClientReport(CLIENT_REPORT_FILE, engine.getClients());

        // Instrument report
        Map<String, List<Transaction>> transactionsByInstrument = engine.getTransactions().stream()
            .collect(Collectors.groupingBy(Transaction::getInstrumentId));

        List<String> instrumentIds = new ArrayList<>(instruments.keySet());
        instrumentIds.sort(String::compareTo);

        CSVWriter.writeInstrumentReport(
            INSTRUMENT_REPORT_FILE,
            engine.getOpenPrices(),
            engine.getClosePrices(),
            transactionsByInstrument,
            instrumentIds
        );

        logger.info("All reports generated successfully");
    }
}
