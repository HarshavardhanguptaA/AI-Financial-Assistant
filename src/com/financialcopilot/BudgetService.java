package com.financialcopilot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Service managing monthly budgets and purchase affordability evaluations backed by SQLite.
 */
public class BudgetService {
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private final DatabaseManager dbManager;

    public BudgetService() {
        this(new DatabaseManager());
    }

    public BudgetService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Sets or updates the budget for the current month.
     */
    public void setMonthlyBudget(BigDecimal budget) {
        setMonthlyBudget(YearMonth.now(), budget);
    }

    /**
     * Sets or updates the budget for a specific month in SQLite.
     * Uses ON CONFLICT to avoid duplicate entries for the same month.
     */
    public void setMonthlyBudget(YearMonth month, BigDecimal budget) {
        if (month == null) {
            month = YearMonth.now();
        }
        if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monthly budget must be greater than zero.");
        }

        String sql = """
            INSERT INTO monthly_budgets (month, budget_amount)
            VALUES (?, ?)
            ON CONFLICT(month) DO UPDATE SET budget_amount = excluded.budget_amount;
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, month.format(MONTH_FORMATTER));
            pstmt.setString(2, budget.toPlainString());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[Database Error] Could not save monthly budget: " + e.getMessage());
            throw new RuntimeException("Database error saving budget: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the monthly budget for the current month.
     */
    public BigDecimal getMonthlyBudget() {
        return getMonthlyBudget(YearMonth.now());
    }

    /**
     * Retrieves the monthly budget for a specific month from SQLite.
     */
    public BigDecimal getMonthlyBudget(YearMonth month) {
        if (month == null) {
            month = YearMonth.now();
        }
        String sql = "SELECT budget_amount FROM monthly_budgets WHERE month = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, month.format(MONTH_FORMATTER));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new BigDecimal(rs.getString("budget_amount"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[Database Error] Could not fetch budget: " + e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    /**
     * Checks if a monthly budget is set for the current month.
     */
    public boolean isBudgetSet() {
        return isBudgetSet(YearMonth.now());
    }

    /**
     * Checks if a monthly budget is set for a specific month.
     */
    public boolean isBudgetSet(YearMonth month) {
        return getMonthlyBudget(month).compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Calculates remaining budget for a specific month: Monthly Budget - Spent.
     */
    public BigDecimal getRemainingBudget(YearMonth month, BigDecimal spentInMonth) {
        if (!isBudgetSet(month)) {
            return BigDecimal.ZERO;
        }
        BigDecimal spent = spentInMonth != null ? spentInMonth : BigDecimal.ZERO;
        return getMonthlyBudget(month).subtract(spent);
    }

    /**
     * Calculates remaining budget for current month.
     */
    public BigDecimal getRemainingBudget(BigDecimal spentThisMonth) {
        return getRemainingBudget(YearMonth.now(), spentThisMonth);
    }

    /**
     * Calculates budget usage percentage for a specific month safely.
     * Formula: (Spent / Budget) * 100
     */
    public BigDecimal getBudgetUsagePercentage(YearMonth month, BigDecimal spentInMonth) {
        BigDecimal budget = getMonthlyBudget(month);
        if (budget.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal spent = spentInMonth != null ? spentInMonth : BigDecimal.ZERO;
        return spent
                .divide(budget, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates budget usage percentage safely for the current month.
     * Formula: (Spent / Budget) * 100
     */
    public BigDecimal getBudgetUsagePercentage(BigDecimal spentThisMonth) {
        return getBudgetUsagePercentage(YearMonth.now(), spentThisMonth);
    }

    /**
     * Represents the outcome of an affordability check.
     */
    public static class AffordabilityReport {
        private final BigDecimal currentBalance;
        private final BigDecimal purchaseCost;
        private final BigDecimal estimatedBalanceAfter;
        private final BigDecimal remainingBudgetBefore;
        private final BigDecimal estimatedRemainingBudgetAfter;
        private final boolean budgetConfigured;
        private final String assessment;

        public AffordabilityReport(BigDecimal currentBalance,
                                   BigDecimal purchaseCost,
                                   BigDecimal estimatedBalanceAfter,
                                   BigDecimal remainingBudgetBefore,
                                   BigDecimal estimatedRemainingBudgetAfter,
                                   boolean budgetConfigured,
                                   String assessment) {
            this.currentBalance = currentBalance;
            this.purchaseCost = purchaseCost;
            this.estimatedBalanceAfter = estimatedBalanceAfter;
            this.remainingBudgetBefore = remainingBudgetBefore;
            this.estimatedRemainingBudgetAfter = estimatedRemainingBudgetAfter;
            this.budgetConfigured = budgetConfigured;
            this.assessment = assessment;
        }

        public BigDecimal getCurrentBalance() {
            return currentBalance;
        }

        public BigDecimal getPurchaseCost() {
            return purchaseCost;
        }

        public BigDecimal getEstimatedBalanceAfter() {
            return estimatedBalanceAfter;
        }

        public BigDecimal getRemainingBudgetBefore() {
            return remainingBudgetBefore;
        }

        public BigDecimal getEstimatedRemainingBudgetAfter() {
            return estimatedRemainingBudgetAfter;
        }

        public boolean isBudgetConfigured() {
            return budgetConfigured;
        }

        public String getAssessment() {
            return assessment;
        }
    }

    /**
     * Evaluates planned purchase affordability based on current balance and budget status.
     */
    public AffordabilityReport evaluateAffordability(BigDecimal purchaseCost,
                                                    BigDecimal currentBalance,
                                                    BigDecimal spentThisMonth) {
        if (purchaseCost == null || purchaseCost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase cost must be greater than zero.");
        }

        BigDecimal safeCurrentBalance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
        BigDecimal safeSpentThisMonth = spentThisMonth != null ? spentThisMonth : BigDecimal.ZERO;

        BigDecimal estimatedBalanceAfter = safeCurrentBalance.subtract(purchaseCost);
        boolean budgetConfigured = isBudgetSet();
        BigDecimal remainingBudgetBefore = budgetConfigured ? getRemainingBudget(safeSpentThisMonth) : BigDecimal.ZERO;
        BigDecimal estimatedBudgetAfter = budgetConfigured ? remainingBudgetBefore.subtract(purchaseCost) : BigDecimal.ZERO;

        StringBuilder explanation = new StringBuilder();

        if (safeCurrentBalance.compareTo(purchaseCost) < 0) {
            explanation.append(String.format(
                    "You currently have Rs. %.2f available. This purchase of Rs. %.2f exceeds your recorded balance by Rs. %.2f. " +
                    "Making this purchase would lead to a negative balance of Rs. %.2f. " +
                    "It is strongly recommended to postpone this expense until you receive more income.",
                    safeCurrentBalance, purchaseCost, purchaseCost.subtract(safeCurrentBalance), estimatedBalanceAfter
            ));
        } else {
            explanation.append(String.format(
                    "You currently have Rs. %.2f available. This purchase would leave an estimated balance of Rs. %.2f. ",
                    safeCurrentBalance, estimatedBalanceAfter
            ));

            if (budgetConfigured) {
                if (estimatedBudgetAfter.compareTo(BigDecimal.ZERO) < 0) {
                    explanation.append(String.format(
                            "However, this purchase would exceed your monthly budget by Rs. %.2f. " +
                            "While you have sufficient cash in your balance, it would break your monthly spending limit. ",
                            estimatedBudgetAfter.abs()
                    ));
                } else {
                    explanation.append(String.format(
                            "It also fits within your monthly budget, leaving Rs. %.2f remaining in your budget for this month. ",
                            estimatedBudgetAfter
                    ));
                }
            }

            explanation.append("Please check any upcoming fixed expenses (like rent, bills, or food) before making this decision.");
        }

        explanation.append("\n[Note: This result is a basic estimate based strictly on recorded transactions and the current budget, not a guaranteed financial recommendation.]");

        return new AffordabilityReport(
                currentBalance,
                purchaseCost,
                estimatedBalanceAfter,
                remainingBudgetBefore,
                estimatedBudgetAfter,
                budgetConfigured,
                explanation.toString()
        );
    }
}
