package com.financialcopilot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smart Financial Assistant - rule-based conversational engine.
 * Processes natural-language queries and returns contextual, data-driven
 * financial responses by querying FinanceService and BudgetService in real
 * time.
 */
public class AssistantService {

    private static final DateTimeFormatter MONTH_DISPLAY = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FinanceService financeService;
    private final BudgetService budgetService;

    public AssistantService(FinanceService financeService, BudgetService budgetService) {
        this.financeService = financeService;
        this.budgetService = budgetService;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Main entry point: takes raw user message, returns assistant response. */
    public String respond(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "I didn't catch that. Could you rephrase your question?";
        }
        String msg = userMessage.trim().toLowerCase();

        if (matchesAny(msg, "hello", "hi", "hey", "howdy", "good morning", "good evening", "namaste", "greetings"))
            return handleGreeting();

        if (matchesAny(msg, "help", "what can you do", "capabilities", "commands", "options",
                "what can i ask", "how do you work", "what do you know"))
            return handleHelp();

        if (matchesAny(msg, "balance", "how much do i have", "available", "funds", "cash",
                "how much money", "money left", "what is my balance"))
            return handleBalance();

        if (matchesAny(msg, "income", "earning", "earned", "salary", "stipend", "total income",
                "how much have i earned", "month income"))
            return handleIncome(msg);

        if (matchesAny(msg, "expense", "spent", "spending", "expenditure", "total spent",
                "how much have i spent", "outgoing", "outflow"))
            return handleExpenses(msg);

        if (matchesAny(msg, "budget", "limit", "monthly limit", "budget left", "remaining budget",
                "budget remaining", "how much budget", "budget status"))
            return handleBudget();

        if (matchesAny(msg, "saving", "savings", "save", "saved", "how much have i saved", "savings rate",
                "saving rate"))
            return handleSavings();

        if (matchesAny(msg, "category", "categories", "breakdown", "where am i spending",
                "top spending", "spend by", "spending breakdown", "where did i spend"))
            return handleCategoryBreakdown();

        if (matchesAny(msg, "afford", "can i buy", "should i buy", "purchase", "worth buying",
                "should i spend", "can i spend", "buy this", "spend on"))
            return handleAffordabilityQuery(msg);

        if (matchesAny(msg, "insight", "tip", "advice", "recommend", "suggestion", "financial health",
                "how am i doing", "status", "assessment", "performance", "review"))
            return handleInsights();

        if (matchesAny(msg, "transaction", "history", "recent", "list transactions",
                "show transactions", "last transactions", "previous", "records"))
            return handleTransactionHistory(msg);

        if (matchesAny(msg, "overspent", "over budget", "exceeded", "exceed budget",
                "budget breached", "above budget", "gone over"))
            return handleBudgetAlert();

        if (matchesAny(msg, "percent", "percentage", "ratio", "rate", "utilization", "utilisation"))
            return handlePercentages();

        YearMonth monthInMsg = matchesMonth(msg);
        if (monthInMsg != null)
            return handleMonthQuery(monthInMsg);

        return handleFallback(userMessage);
    }

    // =========================================================================
    // Intent Handlers
    // =========================================================================

    private String handleGreeting() {
        if (!financeService.hasTransactions()) {
            return "Hi! I am your Financial Copilot Assistant.\n\n"
                    + "It looks like you have not recorded any transactions yet. "
                    + "Add your income or expenses using the dashboard, then ask me anything!\n\n"
                    + "Try asking:\n"
                    + "- What is my balance?\n"
                    + "- How much have I spent this month?\n"
                    + "- Am I within my budget?";
        }
        BigDecimal balance = financeService.getAvailableBalance();
        BigDecimal spent = financeService.getMonthExpenses(YearMonth.now());
        return String.format(
                "Hello! I am your Financial Copilot Assistant.\n\n"
                        + "Quick snapshot:\n"
                        + "- Available Balance: Rs.%s\n"
                        + "- Spent This Month: Rs.%s\n\n"
                        + "Ask me anything about your finances!",
                fmt(balance), fmt(spent));
    }

    private String handleHelp() {
        return "What I can help you with:\n\n"
                + "Balance & Finances\n"
                + "- What is my current balance?\n"
                + "- How much have I earned / spent?\n"
                + "- What is my savings rate?\n\n"
                + "Budget\n"
                + "- What is my monthly budget?\n"
                + "- Am I over budget?\n"
                + "- How much budget do I have left?\n\n"
                + "Spending Categories\n"
                + "- Where am I spending the most?\n"
                + "- Show me this month's category breakdown\n\n"
                + "Purchase Advice\n"
                + "- Can I afford to spend Rs.2000?\n"
                + "- Should I buy something for Rs.5000?\n\n"
                + "Transactions & Insights\n"
                + "- Show me my recent transactions\n"
                + "- Give me financial insights\n"
                + "- How am I doing financially?";
    }

    private String handleBalance() {
        if (!financeService.hasTransactions()) {
            return "You have not recorded any transactions yet, so your balance is Rs.0.00.\n\n"
                    + "Add your income and expenses using the dashboard to track your balance.";
        }
        BigDecimal totalIncome = financeService.getTotalIncome();
        BigDecimal totalExpenses = financeService.getTotalExpenses();
        BigDecimal balance = financeService.getAvailableBalance();
        String status;
        if (balance.compareTo(BigDecimal.ZERO) > 0)
            status = "You are in a positive balance - great work!";
        else if (balance.compareTo(BigDecimal.ZERO) == 0)
            status = "Your income and expenses are exactly balanced.";
        else
            status = "Warning: Your expenses exceed your recorded income (negative balance).";
        return String.format(
                "Your Current Balance\n\n"
                        + "- Total Recorded Income:   Rs.%s\n"
                        + "- Total Recorded Expenses: Rs.%s\n"
                        + "- Available Balance:       Rs.%s\n\n"
                        + "%s",
                fmt(totalIncome), fmt(totalExpenses), fmt(balance), status);
    }

    private String handleIncome(String msg) {
        if (!financeService.hasIncome()) {
            return "You have not recorded any income yet.\n\n"
                    + "Use the Record Transaction form and select Income to add your salary, stipend, or other earnings.";
        }
        YearMonth now = YearMonth.now();
        BigDecimal totalIncome = financeService.getTotalIncome();
        BigDecimal monthIncome = financeService.getMonthIncome(now);
        return String.format(
                "Income Summary\n\n"
                        + "- All-Time Total Income:    Rs.%s\n"
                        + "- Income This Month (%s): Rs.%s\n\n"
                        + "Ask \"What is my savings rate?\" for a deeper analysis.",
                fmt(totalIncome), now.format(MONTH_DISPLAY), fmt(monthIncome));
    }

    private String handleExpenses(String msg) {
        if (!financeService.hasExpenses()) {
            return "You have not recorded any expenses yet.\n\n"
                    + "Use the Record Transaction form with Expense type to start tracking your spending.";
        }
        YearMonth now = YearMonth.now();
        BigDecimal totalExpenses = financeService.getTotalExpenses();
        BigDecimal monthExpenses = financeService.getMonthExpenses(now);
        Map.Entry<String, BigDecimal> top = financeService.getHighestSpendingCategory(now);
        String topStr = (top != null)
                ? "\n- Top Category This Month: " + top.getKey() + " (Rs." + fmt(top.getValue()) + ")"
                : "";
        return String.format(
                "Expense Summary\n\n"
                        + "- All-Time Total Expenses: Rs.%s\n"
                        + "- This Month (%s):       Rs.%s%s\n\n"
                        + "Ask \"Show me my category breakdown\" for a detailed split.",
                fmt(totalExpenses), now.format(MONTH_DISPLAY), fmt(monthExpenses), topStr);
    }

    private String handleBudget() {
        YearMonth now = YearMonth.now();
        if (!budgetService.isBudgetSet(now)) {
            return "No Monthly Budget Set for " + now.format(MONTH_DISPLAY) + ".\n\n"
                    + "Use the Monthly Budget Progress card on the dashboard to set a spending limit. "
                    + "Once set, I can tell you how much budget remains and warn you when you are close to the limit!";
        }
        BigDecimal budget = budgetService.getMonthlyBudget(now);
        BigDecimal spent = financeService.getMonthExpenses(now);
        BigDecimal remaining = budgetService.getRemainingBudget(now, spent);
        BigDecimal usagePct = budgetService.getBudgetUsagePercentage(now, spent);
        String status;
        if (spent.compareTo(budget) > 0)
            status = "OVER BUDGET - You have exceeded your budget by Rs." + fmt(spent.subtract(budget)) + "!";
        else if (usagePct.compareTo(BigDecimal.valueOf(80)) >= 0)
            status = "Caution - You have used over 80% of your budget. Spend carefully!";
        else
            status = "On Track - You are within your budget. Great discipline!";
        return String.format(
                "Budget Status - %s\n\n"
                        + "- Monthly Budget: Rs.%s\n"
                        + "- Spent So Far:   Rs.%s (%.2f%%)\n"
                        + "- Remaining:      Rs.%s\n\n"
                        + "%s",
                now.format(MONTH_DISPLAY), fmt(budget), fmt(spent), usagePct, fmt(remaining), status);
    }

    private String handleSavings() {
        if (!financeService.hasIncome()) {
            return "No income recorded yet, so I cannot compute your savings rate.\n\n"
                    + "Record your income first using the dashboard, then ask again!";
        }
        BigDecimal totalIncome = financeService.getTotalIncome();
        BigDecimal totalExpenses = financeService.getTotalExpenses();
        BigDecimal savings = totalIncome.subtract(totalExpenses);
        BigDecimal savingsRate = totalIncome.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : savings.divide(totalIncome, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
        YearMonth now = YearMonth.now();
        BigDecimal mIncome = financeService.getMonthIncome(now);
        BigDecimal mExpenses = financeService.getMonthExpenses(now);
        BigDecimal mSavings = mIncome.subtract(mExpenses);
        String rating;
        if (savingsRate.compareTo(BigDecimal.valueOf(30)) >= 0)
            rating = "Excellent saver! A savings rate above 30% is outstanding.";
        else if (savingsRate.compareTo(BigDecimal.valueOf(20)) >= 0)
            rating = "Good savings rate! Financial experts recommend saving at least 20%.";
        else if (savingsRate.compareTo(BigDecimal.valueOf(10)) >= 0)
            rating = "Moderate savings. Try reducing discretionary spending to push above 20%.";
        else if (savingsRate.compareTo(BigDecimal.ZERO) > 0)
            rating = "Low savings rate. Look for areas to cut spending and boost savings.";
        else
            rating = "Negative savings! Your expenses exceed your income. Review your spending urgently.";
        return String.format(
                "Savings Summary\n\n"
                        + "All-Time:\n"
                        + "- Income: Rs.%s  Expenses: Rs.%s\n"
                        + "- Total Savings: Rs.%s\n"
                        + "- Savings Rate:  %.2f%%\n\n"
                        + "This Month (%s):\n"
                        + "- Income: Rs.%s  Expenses: Rs.%s\n"
                        + "- Net Savings: Rs.%s\n\n"
                        + "%s",
                fmt(totalIncome), fmt(totalExpenses), fmt(savings), savingsRate,
                now.format(MONTH_DISPLAY), fmt(mIncome), fmt(mExpenses), fmt(mSavings), rating);
    }

    private String handleCategoryBreakdown() {
        YearMonth now = YearMonth.now();
        Map<String, BigDecimal> cats = financeService.getCategoryExpensesForMonth(now);
        if (cats.isEmpty()) {
            return "No expense categories recorded for " + now.format(MONTH_DISPLAY) + " yet.\n\n"
                    + "Add some expenses via the dashboard and I will show you where your money goes!";
        }
        BigDecimal total = financeService.getMonthExpenses(now);
        List<Map.Entry<String, BigDecimal>> sorted = new ArrayList<>(cats.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        StringBuilder sb = new StringBuilder("Spending by Category - " + now.format(MONTH_DISPLAY) + "\n\n");
        String[] ranks = { "1st", "2nd", "3rd", "4th", "5th", "6th", "7th" };
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, BigDecimal> e = sorted.get(i);
            BigDecimal pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? e.getValue().divide(total, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            sb.append(String.format("  %s %s - Rs.%s (%.1f%%)\n",
                    i < ranks.length ? ranks[i] : "-", e.getKey(), fmt(e.getValue()), pct));
        }
        sb.append("\nTotal Spent: Rs.").append(fmt(total)).append("\n\n");
        sb.append("Your biggest spend is ").append(sorted.get(0).getKey())
                .append(" at Rs.").append(fmt(sorted.get(0).getValue())).append(".");
        return sb.toString();
    }

    private String handleAffordabilityQuery(String msg) {
        BigDecimal amount = extractAmount(msg);
        if (amount == null) {
            return "I would love to help evaluate a purchase! Please include the amount.\n\n"
                    + "For example:\n"
                    + "- Can I afford to spend Rs.2000?\n"
                    + "- Should I buy something for Rs.5500?\n\n"
                    + "Or use the Purchase Affordability Checker card on the dashboard.";
        }
        BigDecimal currentBalance = financeService.getAvailableBalance();
        YearMonth now = YearMonth.now();
        BigDecimal spentThisMonth = financeService.getMonthExpenses(now);
        BudgetService.AffordabilityReport report = budgetService.evaluateAffordability(amount, currentBalance,
                spentThisMonth);
        String verdict;
        if (currentBalance.compareTo(amount) < 0)
            verdict = "NOT RECOMMENDED - This purchase exceeds your available balance.";
        else if (report.isBudgetConfigured()
                && report.getEstimatedRemainingBudgetAfter().compareTo(BigDecimal.ZERO) < 0)
            verdict = "CAUTION - You have the balance, but this would exceed your monthly budget.";
        else
            verdict = "LOOKS AFFORDABLE - This purchase fits within your balance and budget.";
        StringBuilder sb = new StringBuilder();
        sb.append("Purchase Affordability - Rs.").append(fmt(amount)).append("\n\n");
        sb.append(verdict).append("\n\n");
        sb.append("- Current Balance:        Rs.").append(fmt(currentBalance)).append("\n");
        sb.append("- Balance After Purchase: Rs.").append(fmt(report.getEstimatedBalanceAfter())).append("\n");
        if (report.isBudgetConfigured()) {
            sb.append("- Budget Remaining After: Rs.")
                    .append(fmt(report.getEstimatedRemainingBudgetAfter())).append("\n");
        }
        sb.append("\nAlways account for upcoming fixed expenses before making a purchase decision.");
        return sb.toString();
    }

    private String handleInsights() {
        List<String> insights = financeService.getSpendingInsights(budgetService);
        if (insights.isEmpty())
            return "No insights available yet. Start by recording your income and expenses!";
        StringBuilder sb = new StringBuilder("Financial Insights\n\n");
        for (String insight : insights) {
            sb.append("- ").append(insight).append("\n");
        }
        YearMonth now = YearMonth.now();
        if (budgetService.isBudgetSet(now)) {
            BigDecimal spent = financeService.getMonthExpenses(now);
            BigDecimal remaining = budgetService.getRemainingBudget(now, spent);
            if (remaining.compareTo(BigDecimal.ZERO) > 0)
                sb.append("\nTip: You have Rs.").append(fmt(remaining))
                        .append(" left in your budget for ").append(now.format(MONTH_DISPLAY))
                        .append(". Use it wisely or carry it over to next month!");
        } else {
            sb.append("\nTip: Set a monthly budget to unlock personalised spending alerts!");
        }
        return sb.toString();
    }

    private String handleTransactionHistory(String msg) {
        List<Transaction> txns = financeService.getAllTransactions();
        if (txns.isEmpty()) {
            return "No transactions recorded yet.\n\n"
                    + "Start by adding your income and expenses using the Record Transaction form!";
        }
        int limit = matchesAny(msg, "recent", "latest", "last", "newest") ? 5 : 10;
        List<Transaction> display = new ArrayList<>(txns);
        display.sort(Comparator.comparing(Transaction::getDate).reversed()
                .thenComparingInt(Transaction::getId).reversed());
        if (display.size() > limit)
            display = display.subList(0, limit);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        StringBuilder sb = new StringBuilder("Recent Transactions (last " + display.size() + ")\n\n");
        for (Transaction t : display) {
            String arrow = t.getType() == TransactionType.INCOME ? "[IN] " : "[OUT]";
            sb.append(String.format("%s %s | Rs.%s | %s | %s\n",
                    arrow, t.getDate().format(dateFmt), fmt(t.getAmount()),
                    t.getCategoryOrSource(), t.getDescription()));
        }
        sb.append("\nShowing ").append(display.size()).append(" of ").append(txns.size())
                .append(" total transactions. See the full list in the Transaction History table.");
        return sb.toString();
    }

    private String handleBudgetAlert() {
        YearMonth now = YearMonth.now();
        if (!budgetService.isBudgetSet(now)) {
            return "No budget set for " + now.format(MONTH_DISPLAY) + ".\n\n"
                    + "Set a budget using the dashboard to track whether you are overspending!";
        }
        BigDecimal budget = budgetService.getMonthlyBudget(now);
        BigDecimal spent = financeService.getMonthExpenses(now);
        BigDecimal remaining = budgetService.getRemainingBudget(now, spent);
        BigDecimal pct = budgetService.getBudgetUsagePercentage(now, spent);
        if (spent.compareTo(budget) > 0) {
            return String.format(
                    "Budget Exceeded for %s!\n\n"
                            + "You are Rs.%s OVER your monthly budget.\n\n"
                            + "- Budget: Rs.%s\n"
                            + "- Spent:  Rs.%s (%.2f%%)\n\n"
                            + "Consider reducing spending in Food, Shopping, or Entertainment to stay on track next month.",
                    now.format(MONTH_DISPLAY), fmt(spent.subtract(budget)),
                    fmt(budget), fmt(spent), pct);
        }
        return String.format(
                "You are within your budget for %s!\n\n"
                        + "- Budget:    Rs.%s\n"
                        + "- Spent:     Rs.%s (%.2f%%)\n"
                        + "- Remaining: Rs.%s\n\n"
                        + "%s",
                now.format(MONTH_DISPLAY), fmt(budget), fmt(spent), pct, fmt(remaining),
                pct.compareTo(BigDecimal.valueOf(80)) >= 0
                        ? "You are close to your limit. Spend carefully!"
                        : "Keep up the disciplined spending!");
    }

    private String handleMonthQuery(YearMonth target) {
        BigDecimal income = financeService.getMonthIncome(target);
        BigDecimal expenses = financeService.getMonthExpenses(target);
        BigDecimal net = income.subtract(expenses);
        BigDecimal budget = budgetService.getMonthlyBudget(target);
        Map<String, BigDecimal> cats = financeService.getCategoryExpensesForMonth(target);
        StringBuilder sb = new StringBuilder("Financial Summary - " + target.format(MONTH_DISPLAY) + "\n\n");
        sb.append("- Income:   Rs.").append(fmt(income)).append("\n");
        sb.append("- Expenses: Rs.").append(fmt(expenses)).append("\n");
        sb.append("- Net:      Rs.").append(fmt(net))
                .append(net.compareTo(BigDecimal.ZERO) >= 0 ? " (surplus)" : " (deficit)").append("\n");
        if (budget.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("- Budget:   Rs.").append(fmt(budget))
                    .append(" | Remaining: Rs.").append(fmt(budget.subtract(expenses))).append("\n");
        } else {
            sb.append("- Budget: Not set for this month\n");
        }
        if (!cats.isEmpty()) {
            sb.append("\nTop Categories:\n");
            cats.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(3)
                    .forEach(e -> sb.append("  - ").append(e.getKey())
                            .append(": Rs.").append(fmt(e.getValue())).append("\n"));
        }
        return sb.toString();
    }

    private String handlePercentages() {
        BigDecimal totalIncome = financeService.getTotalIncome();
        BigDecimal totalExpenses = financeService.getTotalExpenses();
        if (totalIncome.compareTo(BigDecimal.ZERO) == 0)
            return "No income recorded yet. I cannot compute ratios without income data.";
        BigDecimal savingsRate = totalIncome.subtract(totalExpenses)
                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal spendingRate = totalExpenses
                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        YearMonth now = YearMonth.now();
        StringBuilder sb = new StringBuilder("Financial Ratios\n\n");
        sb.append("- Spending Rate (All-Time): ").append(spendingRate).append("%\n");
        sb.append("- Savings Rate  (All-Time): ").append(savingsRate).append("%\n");
        if (budgetService.isBudgetSet(now)) {
            BigDecimal spent = financeService.getMonthExpenses(now);
            BigDecimal usage = budgetService.getBudgetUsagePercentage(now, spent);
            sb.append("- Budget Utilization (").append(now.format(MONTH_DISPLAY)).append("): ")
                    .append(usage).append("%\n");
        }
        return sb.toString();
    }

    private String handleFallback(String originalMessage) {
        String display = originalMessage.length() > 60
                ? originalMessage.substring(0, 60) + "..."
                : originalMessage;
        return "I am not sure how to answer: \"" + display + "\"\n\n"
                + "Here are some things I can help with:\n"
                + "- What is my balance?\n"
                + "- How much have I spent this month?\n"
                + "- What is my savings rate?\n"
                + "- Am I within my budget?\n"
                + "- Where am I spending the most?\n"
                + "- Can I afford to spend Rs.3000?\n"
                + "- Show me my recent transactions\n\n"
                + "Type 'help' to see all available questions.";
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean matchesAny(String msg, String... keywords) {
        for (String kw : keywords) {
            if (msg.contains(kw))
                return true;
        }
        return false;
    }

    private BigDecimal extractAmount(String msg) {
        Matcher m = Pattern.compile("[rR][sS]\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)").matcher(msg);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1).replace(",", ""));
            } catch (Exception ignored) {
            }
        }
        Matcher m2 = Pattern.compile("\\b(\\d{2,7}(?:\\.\\d{1,2})?)\\b").matcher(msg);
        if (m2.find()) {
            try {
                BigDecimal v = new BigDecimal(m2.group(1));
                if (v.compareTo(BigDecimal.ZERO) > 0)
                    return v;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private YearMonth matchesMonth(String msg) {
        String[] full = { "january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december" };
        String[] abbr = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec" };
        int year = LocalDate.now().getYear();
        for (int i = 0; i < full.length; i++)
            if (msg.contains(full[i]))
                return YearMonth.of(year, i + 1);
        for (int i = 0; i < abbr.length; i++)
            if (msg.contains(abbr[i]))
                return YearMonth.of(year, i + 1);
        Matcher m = Pattern.compile("\\b(20\\d{2})-(0[1-9]|1[0-2])\\b").matcher(msg);
        if (m.find()) {
            try {
                return YearMonth.parse(m.group(), MONTH_KEY);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String fmt(BigDecimal val) {
        if (val == null)
            return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
