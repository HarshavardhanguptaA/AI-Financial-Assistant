package com.financialcopilot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP Web Server providing REST APIs and static file hosting
 * for the AI Financial Copilot Web Dashboard.
 */
public class WebServer {
    public static final int DEFAULT_PORT = 8080;
    private static final String PUBLIC_DIR = "public";

    private final int port;
    private final String allowedOrigin;
    private final DatabaseManager dbManager;
    private final FinanceService financeService;
    private final BudgetService budgetService;
    private final AssistantService assistantService;
    private final Gson gson;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;

    public WebServer() {
        this(DEFAULT_PORT, new DatabaseManager());
    }

    public WebServer(int port) {
        this(port, new DatabaseManager());
    }

    public WebServer(int port, DatabaseManager dbManager) {
        this(port, dbManager, System.getenv("ALLOWED_ORIGIN"));
    }

    public WebServer(int port, DatabaseManager dbManager, String allowedOrigin) {
        this.port = port;
        this.dbManager = dbManager;
        this.allowedOrigin = (allowedOrigin != null && !allowedOrigin.trim().isEmpty())
                ? allowedOrigin.trim()
                : "*";
        this.financeService = new FinanceService(dbManager);
        this.budgetService = new BudgetService(dbManager);
        this.assistantService = new AssistantService(financeService, budgetService);

        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (src, typeOfSrc, context) ->
                        new JsonPrimitive(src.toString()))
                .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, typeOfT, context) ->
                        LocalDate.parse(json.getAsString()))
                .registerTypeAdapter(BigDecimal.class, (JsonSerializer<BigDecimal>) (src, typeOfSrc, context) ->
                        new JsonPrimitive(src.setScale(2, RoundingMode.HALF_UP).toPlainString()))
                .registerTypeAdapter(BigDecimal.class, (JsonDeserializer<BigDecimal>) (json, typeOfT, context) ->
                        new BigDecimal(json.getAsString()))
                .setPrettyPrinting()
                .create();
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        String dbFile = System.getenv("DB_PATH");
        if (dbFile == null || dbFile.trim().isEmpty()) {
            dbFile = DatabaseManager.DEFAULT_DB_NAME;
        }

        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (args != null && args.length > 1 && !args[1].trim().isEmpty()) {
            dbFile = args[1].trim();
        }

        WebServer webServer = new WebServer(port, new DatabaseManager(dbFile));
        try {
            webServer.start();
            System.out.println("==================================================");
            System.out.println("   AI Financial Copilot - Phase 3 Web Server      ");
            System.out.printf("   Listening on: http://0.0.0.0:%d (Port: %d)%n", webServer.getPort(), webServer.getPort());
            System.out.printf("   Database file: %s%n", dbFile);
            System.out.println("   Press Ctrl+C to stop the server               ");
            System.out.println("==================================================");
        } catch (IOException e) {
            System.err.println("[Server Error] Failed to start HTTP server: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // API Endpoints
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/summary", new SummaryHandler());
        server.createContext("/api/transactions", new TransactionsHandler());
        server.createContext("/api/budget", new BudgetHandler());
        server.createContext("/api/affordability", new AffordabilityHandler());
        server.createContext("/api/insights", new InsightsHandler());
        server.createContext("/api/assistant", new AssistantHandler());

        // Static Files Handler (serves public/ directory)
        server.createContext("/", new StaticFileHandler());

        this.executor = Executors.newFixedThreadPool(10, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public int getPort() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        return port;
    }

    public FinanceService getFinanceService() {
        return financeService;
    }

    public BudgetService getBudgetService() {
        return budgetService;
    }

    // =========================================================================
    // HTTP Handlers
    // =========================================================================

    /**
     * GET /api/health
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsAndMethod(exchange, "GET")) return;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "UP");
            response.put("service", "AI Financial Copilot");
            sendJsonResponse(exchange, 200, response);
        }
    }

    /**
     * GET /api/summary
     */
    private class SummaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsAndMethod(exchange, "GET")) return;

            try {
                YearMonth currentMonth = YearMonth.now();
                BigDecimal totalIncome = financeService.getTotalIncome();
                BigDecimal totalExpenses = financeService.getTotalExpenses();
                BigDecimal availableBalance = financeService.getAvailableBalance();
                BigDecimal monthIncome = financeService.getMonthIncome(currentMonth);
                BigDecimal monthExpenses = financeService.getMonthExpenses(currentMonth);
                BigDecimal monthlyBudget = budgetService.getMonthlyBudget(currentMonth);
                BigDecimal remainingBudget = budgetService.getRemainingBudget(currentMonth, monthExpenses);
                BigDecimal usagePercent = budgetService.getBudgetUsagePercentage(currentMonth, monthExpenses);
                Map<String, BigDecimal> categoryExpenses = financeService.getCategoryExpensesForMonth(currentMonth);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("totalIncome", totalIncome);
                response.put("totalExpenses", totalExpenses);
                response.put("availableBalance", availableBalance);
                response.put("monthIncome", monthIncome);
                response.put("monthExpenses", monthExpenses);
                response.put("monthlyBudget", monthlyBudget);
                response.put("remainingBudget", remainingBudget);
                response.put("budgetUsagePercentage", usagePercent);
                response.put("currentMonth", currentMonth.toString());
                response.put("isBudgetSet", budgetService.isBudgetSet(currentMonth));
                response.put("categoryExpenses", categoryExpenses);

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Error computing financial summary: " + e.getMessage());
            }
        }
    }

    /**
     * GET /api/transactions
     * POST /api/transactions
     */
    private class TransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod().toUpperCase();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                handleOptions(exchange);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                try {
                    List<Transaction> transactions = financeService.getAllTransactions();
                    sendJsonResponse(exchange, 200, transactions);
                } catch (Exception e) {
                    sendErrorResponse(exchange, 500, "Error loading transactions: " + e.getMessage());
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                if (body == null || body.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Request body cannot be empty.");
                    return;
                }

                try {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                    if (!json.has("description") || json.get("description").getAsString().trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Transaction description is required.");
                        return;
                    }
                    if (!json.has("amount")) {
                        sendErrorResponse(exchange, 400, "Transaction amount is required.");
                        return;
                    }
                    if (!json.has("type") || json.get("type").getAsString().trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Transaction type is required (INCOME or EXPENSE).");
                        return;
                    }
                    if (!json.has("category") || json.get("category").getAsString().trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Transaction category or source is required.");
                        return;
                    }

                    String description = json.get("description").getAsString().trim();
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(json.get("amount").getAsString().trim());
                        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                            sendErrorResponse(exchange, 400, "Amount must be strictly greater than zero.");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendErrorResponse(exchange, 400, "Invalid monetary amount format.");
                        return;
                    }

                    TransactionType type;
                    try {
                        type = TransactionType.valueOf(json.get("type").getAsString().trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        sendErrorResponse(exchange, 400, "Invalid transaction type. Must be INCOME or EXPENSE.");
                        return;
                    }

                    String category = json.get("category").getAsString().trim();

                    LocalDate date = LocalDate.now();
                    if (json.has("date") && !json.get("date").getAsString().trim().isEmpty()) {
                        try {
                            date = LocalDate.parse(json.get("date").getAsString().trim());
                        } catch (DateTimeParseException e) {
                            sendErrorResponse(exchange, 400, "Invalid date format. Expected yyyy-MM-dd.");
                            return;
                        }
                    }

                    Transaction txn;
                    if (type == TransactionType.INCOME) {
                        txn = financeService.addIncome(description, amount, category, date);
                    } else {
                        txn = financeService.addExpense(description, amount, category, date);
                    }

                    sendJsonResponse(exchange, 201, txn);
                } catch (Exception e) {
                    sendErrorResponse(exchange, 400, "Malformed JSON or validation error: " + e.getMessage());
                }
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed. Use GET or POST.");
            }
        }
    }

    /**
     * GET /api/budget
     * PUT /api/budget
     */
    private class BudgetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod().toUpperCase();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                handleOptions(exchange);
                return;
            }

            YearMonth currentMonth = YearMonth.now();

            if ("GET".equalsIgnoreCase(method)) {
                try {
                    BigDecimal budget = budgetService.getMonthlyBudget(currentMonth);
                    BigDecimal spent = financeService.getMonthExpenses(currentMonth);
                    BigDecimal remaining = budgetService.getRemainingBudget(currentMonth, spent);
                    BigDecimal usage = budgetService.getBudgetUsagePercentage(currentMonth, spent);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("month", currentMonth.toString());
                    response.put("monthlyBudget", budget);
                    response.put("spentThisMonth", spent);
                    response.put("remainingBudget", remaining);
                    response.put("budgetUsagePercentage", usage);
                    response.put("isBudgetSet", budgetService.isBudgetSet(currentMonth));

                    sendJsonResponse(exchange, 200, response);
                } catch (Exception e) {
                    sendErrorResponse(exchange, 500, "Error loading budget: " + e.getMessage());
                }
            } else if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                if (body == null || body.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Request body cannot be empty.");
                    return;
                }

                try {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (!json.has("amount") && !json.has("budget_amount")) {
                        sendErrorResponse(exchange, 400, "Budget amount is required.");
                        return;
                    }

                    String amountStr = json.has("amount") ? json.get("amount").getAsString() : json.get("budget_amount").getAsString();
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(amountStr.trim());
                        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                            sendErrorResponse(exchange, 400, "Monthly budget must be strictly greater than zero.");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendErrorResponse(exchange, 400, "Invalid budget number format.");
                        return;
                    }

                    YearMonth targetMonth = currentMonth;
                    if (json.has("month") && !json.get("month").getAsString().trim().isEmpty()) {
                        try {
                            targetMonth = YearMonth.parse(json.get("month").getAsString().trim());
                        } catch (DateTimeParseException e) {
                            sendErrorResponse(exchange, 400, "Invalid month format. Expected yyyy-MM.");
                            return;
                        }
                    }

                    budgetService.setMonthlyBudget(targetMonth, amount);

                    BigDecimal spent = financeService.getMonthExpenses(targetMonth);
                    BigDecimal remaining = budgetService.getRemainingBudget(targetMonth, spent);
                    BigDecimal usage = budgetService.getBudgetUsagePercentage(targetMonth, spent);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("month", targetMonth.toString());
                    response.put("monthlyBudget", amount);
                    response.put("spentThisMonth", spent);
                    response.put("remainingBudget", remaining);
                    response.put("budgetUsagePercentage", usage);
                    response.put("isBudgetSet", true);
                    response.put("message", "Monthly budget updated successfully.");

                    sendJsonResponse(exchange, 200, response);
                } catch (Exception e) {
                    sendErrorResponse(exchange, 400, "Malformed JSON or validation error: " + e.getMessage());
                }
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed. Use GET or PUT.");
            }
        }
    }

    /**
     * POST /api/affordability
     */
    private class AffordabilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsAndMethod(exchange, "POST")) return;

            String body = readRequestBody(exchange);
            if (body == null || body.trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Request body cannot be empty.");
                return;
            }

            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                String amountStr = null;
                if (json.has("amount")) {
                    amountStr = json.get("amount").getAsString();
                } else if (json.has("purchaseCost")) {
                    amountStr = json.get("purchaseCost").getAsString();
                }

                if (amountStr == null || amountStr.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Purchase cost amount is required.");
                    return;
                }

                BigDecimal purchaseCost;
                try {
                    purchaseCost = new BigDecimal(amountStr.trim());
                    if (purchaseCost.compareTo(BigDecimal.ZERO) <= 0) {
                        sendErrorResponse(exchange, 400, "Purchase cost must be greater than zero.");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendErrorResponse(exchange, 400, "Invalid monetary number format.");
                    return;
                }

                BigDecimal currentBalance = financeService.getAvailableBalance();
                YearMonth currentMonth = YearMonth.now();
                BigDecimal spentThisMonth = financeService.getMonthExpenses(currentMonth);

                BudgetService.AffordabilityReport report = budgetService.evaluateAffordability(
                        purchaseCost, currentBalance, spentThisMonth
                );

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("currentBalance", report.getCurrentBalance());
                response.put("purchaseCost", report.getPurchaseCost());
                response.put("estimatedBalanceAfter", report.getEstimatedBalanceAfter());
                response.put("remainingBudgetBefore", report.getRemainingBudgetBefore());
                response.put("estimatedRemainingBudgetAfter", report.getEstimatedRemainingBudgetAfter());
                response.put("budgetConfigured", report.isBudgetConfigured());
                response.put("assessment", report.getAssessment());

                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Error evaluating affordability: " + e.getMessage());
            }
        }
    }

    /**
     * GET /api/insights
     */
    private class InsightsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsAndMethod(exchange, "GET")) return;

            try {
                List<String> insights = financeService.getSpendingInsights(budgetService);
                Map<String, Object> response = new HashMap<>();
                response.put("insights", insights);
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Error generating spending insights: " + e.getMessage());
            }
        }
    }

    /**
     * POST /api/assistant
     * Accepts: { "message": "user question" }
     * Returns: { "reply": "assistant response" }
     */
    private class AssistantHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use POST.");
                return;
            }
            String body = readRequestBody(exchange);
            if (body == null || body.trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Request body cannot be empty.");
                return;
            }
            JsonObject json;
            try {
                json = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Malformed JSON request.");
                return;
            }

            if (!json.has("message") || json.get("message").isJsonNull() || json.get("message").getAsString().trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Field 'message' is required and cannot be blank.");
                return;
            }

            try {
                String userMessage = json.get("message").getAsString().trim();
                String reply = assistantService.respond(userMessage);
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("reply", reply);
                sendJsonResponse(exchange, 200, response);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "An internal error occurred while processing your query.");
            }
        }
    }

    /**
     * Static file handler serving public/ folder.
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed.");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Prevent path traversal
            if (path.contains("..")) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            File file = new File(PUBLIC_DIR, path.startsWith("/") ? path.substring(1) : path);
            if (!file.exists() || file.isDirectory()) {
                sendErrorResponse(exchange, 404, "File not found: " + path);
                return;
            }

            String contentType = getMimeType(file.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            byte[] bytes = new byte[(int) file.length()];
            try (InputStream is = new FileInputStream(file)) {
                int read = is.read(bytes);
                if (read != bytes.length) {
                    sendErrorResponse(exchange, 500, "Error reading static file.");
                    return;
                }
            }

            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getMimeType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "text/plain; charset=UTF-8";
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private boolean handleCorsAndMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            handleOptions(exchange);
            return true;
        }
        if (!expectedMethod.equalsIgnoreCase(method)) {
            sendErrorResponse(exchange, 405, "Method not allowed. Expected " + expectedMethod);
            return true;
        }
        return false;
    }

    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String jsonString = gson.toJson(data);
        byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        sendJsonResponse(exchange, statusCode, err);
    }
}
