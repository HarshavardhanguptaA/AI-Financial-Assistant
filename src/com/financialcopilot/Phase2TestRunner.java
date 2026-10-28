package com.financialcopilot;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Automated test runner for Phase 2 SQLite Persistence and regression verification.
 * Uses an isolated temporary SQLite database to avoid modifying user data.
 */
public class Phase2TestRunner {
    private static final String TEST_DB_NAME = "test_phase2_copilot.db";

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" Starting Automated Phase 2 SQLite Persistence Tests ");
        System.out.println("==================================================");

        File dbFile = new File(TEST_DB_NAME);
        if (dbFile.exists()) {
            dbFile.delete();
        }

        int passed = 0;
        int failed = 0;

        try {
            // Test 1: Database and table creation
            System.out.println("\n[Test 1] Verifying Database Initialization & Schema...");
            DatabaseManager dbManager1 = new DatabaseManager(TEST_DB_NAME);
            try (Connection conn = dbManager1.getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('transactions', 'monthly_budgets')");
                int tablesCount = 0;
                while (rs.next()) {
                    tablesCount++;
                }
                assertCondition(tablesCount == 2, "Both 'transactions' and 'monthly_budgets' tables should exist.");
                System.out.println("  PASS: Schema created successfully.");
                passed++;
            }

            // Test 2: Add Income of Rs. 15,000
            System.out.println("\n[Test 2] Adding Income of Rs. 15,000...");
            FinanceService finance1 = new FinanceService(dbManager1);
            Transaction inc = finance1.addIncome("Monthly Salary", new BigDecimal("15000.00"), "Salary", LocalDate.now());
            assertCondition(inc.getId() == 1, "First transaction ID should be 1.");
            assertCondition(inc.getAmount().compareTo(new BigDecimal("15000.00")) == 0, "Amount should be 15000.00.");
            assertCondition(inc.getType() == TransactionType.INCOME, "Type should be INCOME.");
            System.out.println("  PASS: Income of Rs. 15,000 recorded.");
            passed++;

            // Test 3: Add Expense of Rs. 3,000
            System.out.println("\n[Test 3] Adding Expense of Rs. 3,000 for groceries...");
            Transaction exp = finance1.addExpense("Groceries", new BigDecimal("3000.00"), "Food", LocalDate.now());
            assertCondition(exp.getId() == 2, "Second transaction ID should be 2.");
            assertCondition(exp.getAmount().compareTo(new BigDecimal("3000.00")) == 0, "Amount should be 3000.00.");
            assertCondition(exp.getType() == TransactionType.EXPENSE, "Type should be EXPENSE.");
            System.out.println("  PASS: Expense of Rs. 3,000 recorded.");
            passed++;

            // Test 4: Set Monthly Budget of Rs. 5,000
            System.out.println("\n[Test 4] Setting Monthly Budget of Rs. 5,000...");
            BudgetService budget1 = new BudgetService(dbManager1);
            budget1.setMonthlyBudget(new BigDecimal("5000.00"));
            assertCondition(budget1.isBudgetSet(), "Budget should be set.");
            assertCondition(budget1.getMonthlyBudget().compareTo(new BigDecimal("5000.00")) == 0, "Budget should be 5000.00.");
            System.out.println("  PASS: Budget of Rs. 5,000 set.");
            passed++;

            // Test 5: Verify calculations before exit
            System.out.println("\n[Test 5] Verifying Financial Totals (Session 1)...");
            assertCondition(finance1.getTotalIncome().compareTo(new BigDecimal("15000.00")) == 0, "Total income should be 15000.");
            assertCondition(finance1.getTotalExpenses().compareTo(new BigDecimal("3000.00")) == 0, "Total expenses should be 3000.");
            assertCondition(finance1.getAvailableBalance().compareTo(new BigDecimal("12000.00")) == 0, "Available balance should be 12000.");
            assertCondition(finance1.getMonthExpenses(YearMonth.now()).compareTo(new BigDecimal("3000.00")) == 0, "Month expense should be 3000.");
            assertCondition(budget1.getRemainingBudget(new BigDecimal("3000.00")).compareTo(new BigDecimal("2000.00")) == 0, "Remaining budget should be 2000.");
            assertCondition(budget1.getBudgetUsagePercentage(new BigDecimal("3000.00")).compareTo(new BigDecimal("60.00")) == 0, "Usage percentage should be 60%.");
            System.out.println("  PASS: All financial metrics correct before shutdown.");
            passed++;

            // Test 6: SIMULATE APPLICATION RESTART
            System.out.println("\n[Test 6] Simulating Application Restart (Connecting fresh service instances)...");
            DatabaseManager dbManager2 = new DatabaseManager(TEST_DB_NAME);
            FinanceService finance2 = new FinanceService(dbManager2);
            BudgetService budget2 = new BudgetService(dbManager2);

            List<Transaction> restoredTxns = finance2.getAllTransactions();
            assertCondition(restoredTxns.size() == 2, "Expected 2 restored transactions, found " + restoredTxns.size());
            assertCondition(restoredTxns.get(0).getId() == 1, "Txn 1 ID mismatch.");
            assertCondition(restoredTxns.get(0).getDescription().equals("Monthly Salary"), "Txn 1 desc mismatch.");
            assertCondition(restoredTxns.get(0).getAmount().compareTo(new BigDecimal("15000.00")) == 0, "Txn 1 amount mismatch.");
            assertCondition(restoredTxns.get(1).getId() == 2, "Txn 2 ID mismatch.");
            assertCondition(restoredTxns.get(1).getDescription().equals("Groceries"), "Txn 2 desc mismatch.");
            assertCondition(restoredTxns.get(1).getAmount().compareTo(new BigDecimal("3000.00")) == 0, "Txn 2 amount mismatch.");

            assertCondition(finance2.getTotalIncome().compareTo(new BigDecimal("15000.00")) == 0, "Restored total income should be 15000.");
            assertCondition(finance2.getTotalExpenses().compareTo(new BigDecimal("3000.00")) == 0, "Restored total expenses should be 3000.");
            assertCondition(finance2.getAvailableBalance().compareTo(new BigDecimal("12000.00")) == 0, "Restored balance should be 12000.");
            assertCondition(finance2.getMonthExpenses(YearMonth.now()).compareTo(new BigDecimal("3000.00")) == 0, "Restored current-month spending should be 3000.");

            assertCondition(budget2.isBudgetSet(), "Budget should still be set after restart.");
            assertCondition(budget2.getMonthlyBudget().compareTo(new BigDecimal("5000.00")) == 0, "Budget should remain 5000 after restart.");
            System.out.println("  PASS: All transactions and budget fully persisted and verified after restart!");
            passed++;

            // Test 7: Duplicate budget update (updates existing month record without duplicating)
            System.out.println("\n[Test 7] Testing Budget Update for Same Month (No Duplicate Records)...");
            budget2.setMonthlyBudget(new BigDecimal("6000.00"));
            assertCondition(budget2.getMonthlyBudget().compareTo(new BigDecimal("6000.00")) == 0, "Updated budget should be 6000.");
            try (Connection conn = dbManager2.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM monthly_budgets")) {
                rs.next();
                int budgetRowCount = rs.getInt(1);
                assertCondition(budgetRowCount == 1, "Expected exactly 1 budget row in SQLite, found: " + budgetRowCount);
            }
            System.out.println("  PASS: Budget update modified existing record without creating duplicate.");
            passed++;

            // Test 8: Affordability Check with Persisted Data
            System.out.println("\n[Test 8] Testing Purchase Affordability Evaluation...");
            BudgetService.AffordabilityReport affordableReport = budget2.evaluateAffordability(
                    new BigDecimal("1500.00"),
                    finance2.getAvailableBalance(),
                    finance2.getMonthExpenses(YearMonth.now())
            );
            assertCondition(affordableReport.getEstimatedBalanceAfter().compareTo(new BigDecimal("10500.00")) == 0, "Estimated balance after Rs. 1500 purchase should be 10500.");
            assertCondition(affordableReport.getEstimatedRemainingBudgetAfter().compareTo(new BigDecimal("1500.00")) == 0, "Estimated remaining budget after purchase should be 1500.");

            BudgetService.AffordabilityReport unaffordableReport = budget2.evaluateAffordability(
                    new BigDecimal("20000.00"),
                    finance2.getAvailableBalance(),
                    finance2.getMonthExpenses(YearMonth.now())
            );
            assertCondition(unaffordableReport.getEstimatedBalanceAfter().compareTo(BigDecimal.ZERO) < 0, "Rs. 20000 purchase exceeds balance.");
            System.out.println("  PASS: Affordability rules evaluated accurately.");
            passed++;

            // Test 9: Input Validations
            System.out.println("\n[Test 9] Testing Input Validations (Negative/Zero/Empty Values)...");
            assertThrows(() -> finance2.addIncome("", new BigDecimal("100"), "Salary", LocalDate.now()), "Empty income description should throw.");
            assertThrows(() -> finance2.addIncome("Bonus", new BigDecimal("-50"), "Salary", LocalDate.now()), "Negative income should throw.");
            assertThrows(() -> finance2.addIncome("Bonus", BigDecimal.ZERO, "Salary", LocalDate.now()), "Zero income should throw.");
            assertThrows(() -> finance2.addExpense("Snack", new BigDecimal("-10"), "Food", LocalDate.now()), "Negative expense should throw.");
            assertThrows(() -> finance2.addExpense("Snack", new BigDecimal("10"), "", LocalDate.now()), "Empty category should throw.");
            assertThrows(() -> finance2.addIncome("Bonus", new BigDecimal("50"), "Salary", null), "Null date should throw.");
            assertThrows(() -> budget2.setMonthlyBudget(new BigDecimal("-100")), "Negative budget should throw.");
            assertThrows(() -> budget2.setMonthlyBudget(BigDecimal.ZERO), "Zero budget should throw.");
            System.out.println("  PASS: All boundary validations correctly reject invalid inputs.");
            passed++;

            // Test 10: Multi-Month calculation isolation
            System.out.println("\n[Test 10] Testing Multi-Month Calculation Isolation...");
            LocalDate lastMonthDate = LocalDate.now().minusMonths(1);
            finance2.addExpense("Past Month Rent", new BigDecimal("4000.00"), "Bills", lastMonthDate);
            // Current month expense should still be 3000
            assertCondition(finance2.getMonthExpenses(YearMonth.now()).compareTo(new BigDecimal("3000.00")) == 0,
                    "Current month expense should remain 3000 despite past month entry.");
            // Past month expense should be 4000
            assertCondition(finance2.getMonthExpenses(YearMonth.from(lastMonthDate)).compareTo(new BigDecimal("4000.00")) == 0,
                    "Past month expense should be 4000.");
            // Total expenses should be 7000
            assertCondition(finance2.getTotalExpenses().compareTo(new BigDecimal("7000.00")) == 0,
                    "All-time expenses should be 7000.");
            System.out.println("  PASS: Monthly calculations are strictly isolated by transaction_date.");
            passed++;

        } catch (Throwable t) {
            System.err.println("\n[FAIL] Test encountered error: " + t.getMessage());
            t.printStackTrace();
            failed++;
        } finally {
            // Clean up test database files
            try {
                if (dbFile.exists()) {
                    dbFile.delete();
                }
                File walFile = new File(TEST_DB_NAME + "-wal");
                if (walFile.exists()) walFile.delete();
                File shmFile = new File(TEST_DB_NAME + "-shm");
                if (shmFile.exists()) shmFile.delete();
            } catch (Exception ignored) {}
        }

        System.out.println("\n==================================================");
        System.out.printf(" Automated Tests Complete: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
            throw new AssertionError("Expected exception was not thrown: " + message);
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }
}
