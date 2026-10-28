package com.financialcopilot;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service managing financial transactions backed by SQLite persistence.
 */
public class FinanceService {
    private final DatabaseManager dbManager;

    public FinanceService() {
        this(new DatabaseManager());
    }

    public FinanceService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Records and persists a new income transaction to SQLite.
     */
    public Transaction addIncome(String description, BigDecimal amount, String source, LocalDate date) {
        return insertTransaction(TransactionType.INCOME, description, amount, source, date);
    }

    /**
     * Records and persists a new expense transaction to SQLite.
     */
    public Transaction addExpense(String description, BigDecimal amount, String category, LocalDate date) {
        return insertTransaction(TransactionType.EXPENSE, description, amount, category, date);
    }

    private Transaction insertTransaction(TransactionType type, String description, BigDecimal amount, String categoryOrSource, LocalDate date) {
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be empty.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (categoryOrSource == null || categoryOrSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Category or source cannot be empty.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null.");
        }

        String sql = "INSERT INTO transactions (description, amount, type, category, transaction_date) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, description.trim());
            pstmt.setString(2, amount.toPlainString());
            pstmt.setString(3, type.name());
            pstmt.setString(4, categoryOrSource.trim());
            pstmt.setString(5, date.toString());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return new Transaction(id, type, description, amount, categoryOrSource, date);
                } else {
                    throw new SQLException("Failed to retrieve generated transaction ID.");
                }
            }
        } catch (SQLException e) {
            System.err.println("[Database Error] Could not save transaction: " + e.getMessage());
            throw new RuntimeException("Database error saving transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Loads all transactions from the SQLite database.
     */
    public List<Transaction> getAllTransactions() {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT id, description, amount, type, category, transaction_date FROM transactions ORDER BY transaction_date ASC, id ASC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String desc = rs.getString("description");
                BigDecimal amount = new BigDecimal(rs.getString("amount"));
                TransactionType type = TransactionType.valueOf(rs.getString("type"));
                String category = rs.getString("category");
                LocalDate date = LocalDate.parse(rs.getString("transaction_date"));

                list.add(new Transaction(id, type, desc, amount, category, date));
            }
        } catch (SQLException e) {
            System.err.println("[Database Error] Could not load transactions: " + e.getMessage());
        }

        return Collections.unmodifiableList(list);
    }

    /**
     * Returns total income recorded all-time.
     */
    public BigDecimal getTotalIncome() {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : getAllTransactions()) {
            if (t.getType() == TransactionType.INCOME) {
                total = total.add(t.getAmount());
            }
        }
        return total;
    }

    /**
     * Returns total expenses recorded all-time.
     */
    public BigDecimal getTotalExpenses() {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : getAllTransactions()) {
            if (t.getType() == TransactionType.EXPENSE) {
                total = total.add(t.getAmount());
            }
        }
        return total;
    }

    /**
     * Available Balance = Total Income - Total Expenses.
     */
    public BigDecimal getAvailableBalance() {
        return getTotalIncome().subtract(getTotalExpenses());
    }

    /**
     * Calculates income recorded in the specified month and year.
     */
    public BigDecimal getMonthIncome(YearMonth month) {
        YearMonth targetMonth = month != null ? month : YearMonth.now();
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : getAllTransactions()) {
            if (t.getType() == TransactionType.INCOME && YearMonth.from(t.getDate()).equals(targetMonth)) {
                total = total.add(t.getAmount());
            }
        }
        return total;
    }

    /**
     * Calculates expenses recorded in the specified month and year.
     */
    public BigDecimal getMonthExpenses(YearMonth month) {
        YearMonth targetMonth = month != null ? month : YearMonth.now();
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : getAllTransactions()) {
            if (t.getType() == TransactionType.EXPENSE && YearMonth.from(t.getDate()).equals(targetMonth)) {
                total = total.add(t.getAmount());
            }
        }
        return total;
    }

    /**
     * Groups expenses by category for a specific month.
     */
    public Map<String, BigDecimal> getCategoryExpensesForMonth(YearMonth month) {
        YearMonth targetMonth = month != null ? month : YearMonth.now();
        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        for (Transaction t : getAllTransactions()) {
            if (t.getType() == TransactionType.EXPENSE && YearMonth.from(t.getDate()).equals(targetMonth)) {
                String category = t.getCategoryOrSource();
                BigDecimal current = categoryTotals.getOrDefault(category, BigDecimal.ZERO);
                categoryTotals.put(category, current.add(t.getAmount()));
            }
        }
        return categoryTotals;
    }

    /**
     * Finds the category with the highest spending for a given month.
     */
    public Map.Entry<String, BigDecimal> getHighestSpendingCategory(YearMonth month) {
        Map<String, BigDecimal> categoryTotals = getCategoryExpensesForMonth(month);
        Map.Entry<String, BigDecimal> highest = null;
        for (Map.Entry<String, BigDecimal> entry : categoryTotals.entrySet()) {
            if (highest == null || entry.getValue().compareTo(highest.getValue()) > 0) {
                highest = entry;
            }
        }
        return highest;
    }

    /**
     * Check if any transactions have been recorded.
     */
    public boolean hasTransactions() {
        String sql = "SELECT 1 FROM transactions LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            System.err.println("[Database Error] " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if any expenses have been recorded.
     */
    public boolean hasExpenses() {
        String sql = "SELECT 1 FROM transactions WHERE type = ? LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TransactionType.EXPENSE.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[Database Error] " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if any income has been recorded.
     */
    public boolean hasIncome() {
        String sql = "SELECT 1 FROM transactions WHERE type = ? LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TransactionType.INCOME.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[Database Error] " + e.getMessage());
            return false;
        }
    }

    /**
     * Generates rule-based financial insights based on recorded transactions and the current monthly budget.
     */
    public List<String> getSpendingInsights(BudgetService budgetService) {
        List<String> insights = new ArrayList<>();
        if (!hasTransactions()) {
            insights.add("You have not recorded any transactions yet. Start by adding your income or expenses.");
            return insights;
        }

        if (!hasExpenses()) {
            insights.add("You have not recorded any expenses yet.");
        }

        if (!hasIncome()) {
            insights.add("You have not recorded any income yet.");
        }

        BigDecimal totalIncome = getTotalIncome();
        BigDecimal totalExpenses = getTotalExpenses();
        if (hasExpenses() && hasIncome()) {
            if (totalExpenses.compareTo(totalIncome) > 0) {
                insights.add(String.format("Warning: Your expenses (Rs. %.2f) exceed your recorded income (Rs. %.2f) by Rs. %.2f.",
                        totalExpenses, totalIncome, totalExpenses.subtract(totalIncome)));
            } else if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal savings = totalIncome.subtract(totalExpenses);
                BigDecimal savingsRate = savings.divide(totalIncome, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                insights.add(String.format("Healthy Habits: You have saved Rs. %.2f (%.2f%% savings rate of recorded income).",
                        savings, savingsRate));
            }
        }

        YearMonth currentMonth = YearMonth.now();
        Map.Entry<String, BigDecimal> topCategory = getHighestSpendingCategory(currentMonth);
        if (topCategory != null && topCategory.getValue().compareTo(BigDecimal.ZERO) > 0) {
            insights.add(String.format("Your highest spending category this month (%s %d) is %s at Rs. %.2f.",
                    currentMonth.getMonth(), currentMonth.getYear(), topCategory.getKey(), topCategory.getValue()));
        }

        if (budgetService != null && budgetService.isBudgetSet(currentMonth)) {
            BigDecimal spent = getMonthExpenses(currentMonth);
            BigDecimal budget = budgetService.getMonthlyBudget(currentMonth);
            BigDecimal usage = budgetService.getBudgetUsagePercentage(currentMonth, spent);

            insights.add(String.format("You have spent %.2f%% of your monthly budget (Rs. %.2f of Rs. %.2f).",
                    usage, spent, budget));

            if (spent.compareTo(budget) > 0) {
                insights.add(String.format("Attention: You have exceeded your monthly budget by Rs. %.2f.", spent.subtract(budget)));
            } else if (usage.compareTo(BigDecimal.valueOf(80)) >= 0) {
                insights.add("Caution: Your spending has crossed 80% of your budget limit.");
            } else {
                insights.add("On Track: Your monthly spending is within your allocated budget.");
            }
        } else {
            insights.add("Budget Tip: Setting a monthly spending budget in Option 5 will unlock tailored spending limits.");
        }

        return insights;
    }
}
