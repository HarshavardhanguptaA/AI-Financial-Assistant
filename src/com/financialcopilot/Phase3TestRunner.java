package com.financialcopilot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Automated end-to-end test runner for Phase 3 Web Server and REST APIs.
 * Runs against an isolated temporary SQLite database and an ephemeral port.
 */
public class Phase3TestRunner {
    private static final String TEST_DB_NAME = "test_phase3_copilot.db";

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" Starting Automated Phase 3 Web Server & API Tests ");
        System.out.println("==================================================");

        File dbFile = new File(TEST_DB_NAME);
        if (dbFile.exists()) dbFile.delete();

        int passed = 0;
        int failed = 0;
        WebServer server = null;
        int testPort = findFreePort();

        try {
            System.out.printf("Starting WebServer on port %d with database %s...%n", testPort, TEST_DB_NAME);
            server = new WebServer(testPort, new DatabaseManager(TEST_DB_NAME));
            server.start();

            String baseUrl = "http://127.0.0.1:" + testPort;

            // Test 1: Static file serving (GET /)
            System.out.println("\n[Test 1] Testing Static HTML File Serving (GET /)...");
            HttpResponse r1 = sendRequest(baseUrl + "/", "GET", null);
            assertCondition(r1.statusCode == 200, "Expected status 200, got " + r1.statusCode);
            assertCondition(r1.body.contains("Financial Copilot"), "HTML should contain 'Financial Copilot'");
            assertCondition(r1.body.contains("styles.css"), "HTML should link styles.css");
            System.out.println("  PASS: Dashboard HTML served successfully.");
            passed++;

            // Test 2: Initial Summary (GET /api/summary)
            System.out.println("\n[Test 2] Testing Initial Summary (GET /api/summary)...");
            HttpResponse r2 = sendRequest(baseUrl + "/api/summary", "GET", null);
            assertCondition(r2.statusCode == 200, "Expected status 200, got " + r2.statusCode);
            JsonObject sum2 = JsonParser.parseString(r2.body).getAsJsonObject();
            assertCondition(sum2.get("totalIncome").getAsString().equals("0.00"), "Initial income should be 0.00");
            assertCondition(sum2.get("totalExpenses").getAsString().equals("0.00"), "Initial expenses should be 0.00");
            assertCondition(sum2.get("availableBalance").getAsString().equals("0.00"), "Initial balance should be 0.00");
            System.out.println("  PASS: Initial financial summary clean.");
            passed++;

            // Test 3: Add Income of Rs. 15,000 (POST /api/transactions)
            System.out.println("\n[Test 3] Adding Income of Rs. 15,000 (POST /api/transactions)...");
            String incPayload = """
                {
                    "description": "Monthly Stipend",
                    "amount": "15000.00",
                    "type": "INCOME",
                    "category": "Stipend",
                    "date": "2026-10-12"
                }
                """;
            HttpResponse r3 = sendRequest(baseUrl + "/api/transactions", "POST", incPayload);
            assertCondition(r3.statusCode == 201, "Expected status 201 Created, got " + r3.statusCode);
            JsonObject incObj = JsonParser.parseString(r3.body).getAsJsonObject();
            assertCondition(incObj.get("id").getAsInt() == 1, "First txn ID should be 1");
            assertCondition(incObj.get("amount").getAsString().equals("15000.00"), "Amount should be 15000.00");
            System.out.println("  PASS: Income transaction created via API.");
            passed++;

            // Test 4: Add Expense of Rs. 3,000 (POST /api/transactions)
            System.out.println("\n[Test 4] Adding Expense of Rs. 3,000 for groceries (POST /api/transactions)...");
            String expPayload = """
                {
                    "description": "Groceries",
                    "amount": "3000.00",
                    "type": "EXPENSE",
                    "category": "Food",
                    "date": "2026-10-12"
                }
                """;
            HttpResponse r4 = sendRequest(baseUrl + "/api/transactions", "POST", expPayload);
            assertCondition(r4.statusCode == 201, "Expected status 201 Created, got " + r4.statusCode);
            JsonObject expObj = JsonParser.parseString(r4.body).getAsJsonObject();
            assertCondition(expObj.get("id").getAsInt() == 2, "Second txn ID should be 2");
            assertCondition(expObj.get("amount").getAsString().equals("3000.00"), "Amount should be 3000.00");
            System.out.println("  PASS: Expense transaction created via API.");
            passed++;

            // Test 5: Verify Transactions List (GET /api/transactions)
            System.out.println("\n[Test 5] Fetching Transactions List (GET /api/transactions)...");
            HttpResponse r5 = sendRequest(baseUrl + "/api/transactions", "GET", null);
            assertCondition(r5.statusCode == 200, "Expected status 200, got " + r5.statusCode);
            JsonArray txns = JsonParser.parseString(r5.body).getAsJsonArray();
            assertCondition(txns.size() == 2, "Expected 2 transactions, got " + txns.size());
            System.out.println("  PASS: Transactions list returned 2 records.");
            passed++;

            // Test 6: Set Monthly Budget of Rs. 5,000 (PUT /api/budget)
            System.out.println("\n[Test 6] Setting Monthly Budget of Rs. 5,000 (PUT /api/budget)...");
            String budgetPayload = """
                {
                    "amount": "5000.00"
                }
                """;
            HttpResponse r6 = sendRequest(baseUrl + "/api/budget", "PUT", budgetPayload);
            assertCondition(r6.statusCode == 200, "Expected status 200, got " + r6.statusCode);
            JsonObject budObj = JsonParser.parseString(r6.body).getAsJsonObject();
            assertCondition(budObj.get("monthlyBudget").getAsString().equals("5000.00"), "Budget should be 5000.00");
            assertCondition(budObj.get("spentThisMonth").getAsString().equals("3000.00"), "Spent this month should be 3000.00");
            assertCondition(budObj.get("remainingBudget").getAsString().equals("2000.00"), "Remaining budget should be 2000.00");
            assertCondition(budObj.get("budgetUsagePercentage").getAsString().equals("60.00"), "Usage should be 60.00%");
            System.out.println("  PASS: Monthly budget set to Rs. 5,000 with 60% usage.");
            passed++;

            // Test 7: Verify Summary Metrics (GET /api/summary)
            System.out.println("\n[Test 7] Verifying Updated Summary (GET /api/summary)...");
            HttpResponse r7 = sendRequest(baseUrl + "/api/summary", "GET", null);
            assertCondition(r7.statusCode == 200, "Expected status 200, got " + r7.statusCode);
            JsonObject sum7 = JsonParser.parseString(r7.body).getAsJsonObject();
            assertCondition(sum7.get("totalIncome").getAsString().equals("15000.00"), "Total income should be 15000.00");
            assertCondition(sum7.get("totalExpenses").getAsString().equals("3000.00"), "Total expenses should be 3000.00");
            assertCondition(sum7.get("availableBalance").getAsString().equals("12000.00"), "Available balance should be 12000.00");
            assertCondition(sum7.get("monthExpenses").getAsString().equals("3000.00"), "Month expenses should be 3000.00");
            assertCondition(sum7.get("remainingBudget").getAsString().equals("2000.00"), "Remaining budget should be 2000.00");
            assertCondition(sum7.get("budgetUsagePercentage").getAsString().equals("60.00"), "Budget usage should be 60.00%");
            System.out.println("  PASS: Summary metrics perfectly match ₹12,000 balance and ₹3,000 spent.");
            passed++;

            // Test 8: Purchase Affordability Check for Rs. 2,000 (POST /api/affordability)
            System.out.println("\n[Test 8] Checking Affordability for planned purchase of Rs. 2,000 (POST /api/affordability)...");
            String affordPayload = """
                {
                    "amount": "2000.00"
                }
                """;
            HttpResponse r8 = sendRequest(baseUrl + "/api/affordability", "POST", affordPayload);
            assertCondition(r8.statusCode == 200, "Expected status 200, got " + r8.statusCode);
            JsonObject affObj = JsonParser.parseString(r8.body).getAsJsonObject();
            assertCondition(affObj.get("currentBalance").getAsString().equals("12000.00"), "Current balance should be 12000.00");
            assertCondition(affObj.get("purchaseCost").getAsString().equals("2000.00"), "Cost should be 2000.00");
            assertCondition(affObj.get("estimatedBalanceAfter").getAsString().equals("10000.00"), "Balance after should be 10000.00");
            assertCondition(affObj.get("estimatedRemainingBudgetAfter").getAsString().equals("0.00"), "Budget remaining after should be 0.00");
            assertCondition(affObj.get("assessment").getAsString().contains("10000.00"), "Explanation should mention balance 10000.00");
            System.out.println("  PASS: Affordability evaluation evaluated accurately.");
            passed++;

            // Test 9: Rule-based Spending Insights (GET /api/insights)
            System.out.println("\n[Test 9] Fetching Spending Insights (GET /api/insights)...");
            HttpResponse r9 = sendRequest(baseUrl + "/api/insights", "GET", null);
            assertCondition(r9.statusCode == 200, "Expected status 200, got " + r9.statusCode);
            JsonObject insObj = JsonParser.parseString(r9.body).getAsJsonObject();
            JsonArray insArr = insObj.getAsJsonArray("insights");
            assertCondition(insArr.size() > 0, "Insights should not be empty");
            boolean hasSavings = false;
            boolean hasCategory = false;
            boolean hasBudget = false;
            for (int i = 0; i < insArr.size(); i++) {
                String str = insArr.get(i).getAsString();
                if (str.contains("Healthy Habits") || str.contains("savings rate")) hasSavings = true;
                if (str.contains("highest spending category") && str.contains("Food")) hasCategory = true;
                if (str.contains("60.00% of your monthly budget")) hasBudget = true;
            }
            assertCondition(hasSavings, "Should have savings rate insight");
            assertCondition(hasCategory, "Should have Food category insight");
            assertCondition(hasBudget, "Should have 60% budget insight");
            System.out.println("  PASS: Rule-based insights correctly computed.");
            passed++;

            // Test 10: Server Restart Persistence Verification
            System.out.println("\n[Test 10] Testing Server Restart & Data Persistence...");
            server.stop();
            server = null;

            // Start new server on the same database
            int restartPort = findFreePort();
            server = new WebServer(restartPort, new DatabaseManager(TEST_DB_NAME));
            server.start();
            String restartUrl = "http://127.0.0.1:" + restartPort;

            HttpResponse r10 = sendRequest(restartUrl + "/api/summary", "GET", null);
            assertCondition(r10.statusCode == 200, "Expected status 200 after restart");
            JsonObject sum10 = JsonParser.parseString(r10.body).getAsJsonObject();
            assertCondition(sum10.get("totalIncome").getAsString().equals("15000.00"), "Income persisted after restart");
            assertCondition(sum10.get("totalExpenses").getAsString().equals("3000.00"), "Expenses persisted after restart");
            assertCondition(sum10.get("availableBalance").getAsString().equals("12000.00"), "Balance persisted after restart");
            assertCondition(sum10.get("monthlyBudget").getAsString().equals("5000.00"), "Budget persisted after restart");
            System.out.println("  PASS: All data fully persisted in SQLite across server restart!");
            passed++;

            // Test 11: Validation and Error Handling (400 Bad Request)
            System.out.println("\n[Test 11] Testing Validation & Error Handling (Negative amounts, malformed JSON)...");
            HttpResponse r11a = sendRequest(restartUrl + "/api/transactions", "POST", "{\"amount\": \"-50\", \"description\": \"Test\", \"type\": \"INCOME\", \"category\": \"Salary\"}");
            assertCondition(r11a.statusCode == 400, "Negative amount should return 400 Bad Request, got " + r11a.statusCode);

            HttpResponse r11b = sendRequest(restartUrl + "/api/transactions", "POST", "{not-valid-json");
            assertCondition(r11b.statusCode == 400, "Malformed JSON should return 400 Bad Request, got " + r11b.statusCode);

            HttpResponse r11c = sendRequest(restartUrl + "/api/budget", "PUT", "{\"amount\": \"-500\"}");
            assertCondition(r11c.statusCode == 400, "Negative budget should return 400 Bad Request, got " + r11c.statusCode);

            HttpResponse r11d = sendRequest(restartUrl + "/api/nonexistent", "GET", null);
            assertCondition(r11d.statusCode == 404, "Unknown endpoint should return 404, got " + r11d.statusCode);
            System.out.println("  PASS: Server cleanly rejects bad requests with appropriate HTTP status codes.");
            passed++;

            // Test 12: Assistant Endpoint (/api/assistant)
            System.out.println("\n[Test 12] Testing Assistant API (POST /api/assistant)...");
            // 12a. Valid message query
            HttpResponse r12a = sendRequest(restartUrl + "/api/assistant", "POST", "{\"message\": \"What is my balance?\"}");
            assertCondition(r12a.statusCode == 200, "Assistant query should return 200 OK, got " + r12a.statusCode);
            JsonObject asstObj = JsonParser.parseString(r12a.body).getAsJsonObject();
            assertCondition(asstObj.has("reply"), "Assistant response must have 'reply' field");
            assertCondition(asstObj.get("reply").getAsString().contains("12000.00"), "Balance response should include calculated balance of Rs.12000.00");

            // 12b. Empty message validation
            HttpResponse r12b = sendRequest(restartUrl + "/api/assistant", "POST", "{\"message\": \"   \"}");
            assertCondition(r12b.statusCode == 400, "Empty message should return 400 Bad Request, got " + r12b.statusCode);

            // 12c. Missing message field
            HttpResponse r12c = sendRequest(restartUrl + "/api/assistant", "POST", "{}");
            assertCondition(r12c.statusCode == 400, "Missing message field should return 400 Bad Request, got " + r12c.statusCode);

            // 12d. Malformed JSON
            HttpResponse r12d = sendRequest(restartUrl + "/api/assistant", "POST", "{invalid-json");
            assertCondition(r12d.statusCode == 400, "Malformed JSON should return 400 Bad Request, got " + r12d.statusCode);

            // 12e. Unsupported HTTP method
            HttpResponse r12e = sendRequest(restartUrl + "/api/assistant", "GET", null);
            assertCondition(r12e.statusCode == 405, "GET /api/assistant should return 405 Method Not Allowed, got " + r12e.statusCode);

            System.out.println("  PASS: Assistant API behaves correctly under valid and invalid inputs.");
            passed++;

        } catch (Throwable t) {
            System.err.println("\n[FAIL] Test encountered error: " + t.getMessage());
            t.printStackTrace();
            failed++;
        } finally {
            if (server != null) {
                server.stop();
            }
            try {
                if (dbFile.exists()) dbFile.delete();
                File wal = new File(TEST_DB_NAME + "-wal");
                if (wal.exists()) wal.delete();
                File shm = new File(TEST_DB_NAME + "-shm");
                if (shm.exists()) shm.delete();
            } catch (Exception ignored) {}
        }

        System.out.println("\n==================================================");
        System.out.printf(" Phase 3 Automated Tests Complete: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 8089;
        }
    }

    private static HttpResponse sendRequest(String urlStr, String method, String jsonBody) throws IOException {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (jsonBody != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int statusCode = conn.getResponseCode();
        InputStream is = (statusCode >= 200 && statusCode < 400) ? conn.getInputStream() : conn.getErrorStream();
        String body = "";
        if (is != null) {
            try (is) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new HttpResponse(statusCode, body);
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static class HttpResponse {
        final int statusCode;
        final String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
