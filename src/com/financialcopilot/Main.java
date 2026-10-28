package com.financialcopilot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Main application class providing the interactive console menu for AI Financial Copilot.
 */
public class Main {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DatabaseManager dbManager;
    private final FinanceService financeService;
    private final BudgetService budgetService;
    private final Scanner scanner;

    public Main() {
        this(new DatabaseManager());
    }

    public Main(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.financeService = new FinanceService(dbManager);
        this.budgetService = new BudgetService(dbManager);
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        String dbFile = (args != null && args.length > 0 && !args[0].trim().isEmpty())
                ? args[0].trim()
                : DatabaseManager.DEFAULT_DB_NAME;
        Main app = new Main(new DatabaseManager(dbFile));
        app.run();
    }

    /**
     * Main application loop.
     */
    public void run() {
        System.out.println("==================================================");
        System.out.println("   Welcome to AI Financial Copilot (Phase 2)     ");
        System.out.println(" Personal Finance Tracker for First-Time Earners  ");
        System.out.println("==================================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readMenuChoice();

            switch (choice) {
                case 1:
                    handleAddIncome();
                    break;
                case 2:
                    handleAddExpense();
                    break;
                case 3:
                    handleViewAllTransactions();
                    break;
                case 4:
                    handleViewFinancialSummary();
                    break;
                case 5:
                    handleSetMonthlyBudget();
                    break;
                case 6:
                    handleCheckAffordability();
                    break;
                case 7:
                    handleViewSpendingInsights();
                    break;
                case 8:
                    System.out.println("\nThank you for using AI Financial Copilot. Keep building smart financial habits!");
                    running = false;
                    break;
                default:
                    System.out.println("\n[!] Invalid choice. Please choose a number between 1 and 8.");
            }
        }
    }

    /**
     * Displays the primary menu options.
     */
    private void printMenu() {
        System.out.println("\n===== FINANCIAL COPILOT =====");
        System.out.println("1. Add Income");
        System.out.println("2. Add Expense");
        System.out.println("3. View All Transactions");
        System.out.println("4. View Financial Summary");
        System.out.println("5. Set Monthly Budget");
        System.out.println("6. Check Purchase Affordability");
        System.out.println("7. View Spending Insights");
        System.out.println("8. Exit");
        System.out.print("Choose an option: ");
    }

    /**
     * Feature 1: Add Income.
     */
    private void handleAddIncome() {
        System.out.println("\n--- Add Income ---");
        String description = readNonEmptyString("Enter income description (e.g., Monthly Stipend): ");
        BigDecimal amount = readPositiveBigDecimal("Enter amount in INR (Rs.): ");

        System.out.println("Select income source:");
        System.out.println("1. Salary");
        System.out.println("2. Stipend");
        System.out.println("3. Scholarship");
        System.out.println("4. Freelance");
        System.out.println("5. Other");
        String source = selectPredefinedOption("Choose source [1-5]: ",
                new String[]{"Salary", "Stipend", "Scholarship", "Freelance", "Other"});

        LocalDate date = readDateOrDefault("Enter date (yyyy-MM-dd) [Press Enter for today]: ", LocalDate.now());

        Transaction txn = financeService.addIncome(description, amount, source, date);
        System.out.printf("%n[SUCCESS] Added Income! Transaction ID: %d (Rs. %.2f)%n", txn.getId(), txn.getAmount());
    }

    /**
     * Feature 2: Add Expense.
     */
    private void handleAddExpense() {
        System.out.println("\n--- Add Expense ---");
        String description = readNonEmptyString("Enter expense description (e.g., Groceries, Metro pass): ");
        BigDecimal amount = readPositiveBigDecimal("Enter amount in INR (Rs.): ");

        System.out.println("Select expense category:");
        System.out.println("1. Food");
        System.out.println("2. Travel");
        System.out.println("3. Education");
        System.out.println("4. Shopping");
        System.out.println("5. Bills");
        System.out.println("6. Entertainment");
        System.out.println("7. Other");
        String category = selectPredefinedOption("Choose category [1-7]: ",
                new String[]{"Food", "Travel", "Education", "Shopping", "Bills", "Entertainment", "Other"});

        LocalDate date = readDateOrDefault("Enter date (yyyy-MM-dd) [Press Enter for today]: ", LocalDate.now());

        Transaction txn = financeService.addExpense(description, amount, category, date);
        System.out.printf("%n[SUCCESS] Added Expense! Transaction ID: %d (Rs. %.2f)%n", txn.getId(), txn.getAmount());

        // Check if this expense affects the monthly budget
        YearMonth expenseMonth = YearMonth.from(date);
        if (budgetService.isBudgetSet(expenseMonth)) {
            BigDecimal monthExpenses = financeService.getMonthExpenses(expenseMonth);
            BigDecimal budget = budgetService.getMonthlyBudget(expenseMonth);
            BigDecimal usage = budgetService.getBudgetUsagePercentage(expenseMonth, monthExpenses);

            if (monthExpenses.compareTo(budget) > 0) {
                System.out.printf("[!] BUDGET ALERT: You have exceeded your monthly budget by Rs. %.2f! (Total spent this month: Rs. %.2f / Rs. %.2f)%n",
                        monthExpenses.subtract(budget), monthExpenses, budget);
            } else if (usage.compareTo(BigDecimal.valueOf(80)) >= 0) {
                System.out.printf("[!] BUDGET WARNING: You have reached %.2f%% of your monthly budget (Rs. %.2f / Rs. %.2f).%n",
                        usage, monthExpenses, budget);
            }
        }
    }

    /**
     * Feature 3: View All Transactions.
     */
    private void handleViewAllTransactions() {
        System.out.println("\n--- Recorded Transactions ---");
        List<Transaction> transactions = financeService.getAllTransactions();

        if (transactions.isEmpty()) {
            System.out.println("No transactions recorded yet.");
            return;
        }

        System.out.println("+-----+------------+----------------------+----------------+---------+---------------+");
        System.out.printf("| %-3s | %-10s | %-20s | %-14s | %-7s | %-13s |%n",
                "ID", "Date", "Description", "Source/Category", "Type", "Amount (Rs.)");
        System.out.println("+-----+------------+----------------------+----------------+---------+---------------+");

        for (Transaction t : transactions) {
            String desc = truncate(t.getDescription(), 20);
            String cat = truncate(t.getCategoryOrSource(), 14);
            System.out.printf("| %-3d | %-10s | %-20s | %-14s | %-7s | Rs.%10.2f |%n",
                    t.getId(),
                    t.getDate().format(DATE_FORMATTER),
                    desc,
                    cat,
                    t.getType(),
                    t.getAmount());
        }
        System.out.println("+-----+------------+----------------------+----------------+---------+---------------+");
        System.out.printf("Total Transactions: %d%n", transactions.size());
    }

    /**
     * Feature 4: Financial Summary.
     */
    private void handleViewFinancialSummary() {
        System.out.println("\n--- Financial Summary ---");
        BigDecimal totalIncome = financeService.getTotalIncome();
        BigDecimal totalExpenses = financeService.getTotalExpenses();
        BigDecimal availableBalance = financeService.getAvailableBalance();

        YearMonth currentMonth = YearMonth.now();
        BigDecimal monthIncome = financeService.getMonthIncome(currentMonth);
        BigDecimal monthExpenses = financeService.getMonthExpenses(currentMonth);

        System.out.println("----------------------------------------------");
        System.out.println("All-Time Overview:");
        System.out.printf("  Total Income     : Rs. %.2f%n", totalIncome);
        System.out.printf("  Total Expenses   : Rs. %.2f%n", totalExpenses);
        System.out.printf("  Available Balance: Rs. %.2f%n", availableBalance);
        System.out.println("----------------------------------------------");
        System.out.printf("Current Month (%s %d):%n", currentMonth.getMonth(), currentMonth.getYear());
        System.out.printf("  Month's Income   : Rs. %.2f%n", monthIncome);
        System.out.printf("  Month's Expenses : Rs. %.2f%n", monthExpenses);
        System.out.println("----------------------------------------------");

        if (availableBalance.compareTo(BigDecimal.ZERO) < 0) {
            System.out.println("[!] Notice: Your expenses exceed your recorded income. Take care to review spending.");
        }
    }

    /**
     * Feature 5: Set Monthly Budget.
     */
    private void handleSetMonthlyBudget() {
        System.out.println("\n--- Monthly Spending Budget ---");
        if (budgetService.isBudgetSet()) {
            System.out.printf("Current monthly budget: Rs. %.2f%n", budgetService.getMonthlyBudget());
        } else {
            System.out.println("No budget set yet.");
        }

        BigDecimal newBudget = readPositiveBigDecimal("Enter monthly spending budget in INR (Rs.): ");
        budgetService.setMonthlyBudget(newBudget);
        System.out.printf("[SUCCESS] Monthly budget set to Rs. %.2f successfully.%n", newBudget);

        // Display current budget status
        displayBudgetStatus();
    }

    /**
     * Helper to display current month's budget details and warnings.
     */
    private void displayBudgetStatus() {
        YearMonth currentMonth = YearMonth.now();
        BigDecimal spent = financeService.getMonthExpenses(currentMonth);
        BigDecimal budget = budgetService.getMonthlyBudget();
        BigDecimal remaining = budgetService.getRemainingBudget(spent);
        BigDecimal usagePercent = budgetService.getBudgetUsagePercentage(spent);

        System.out.println("\n--- Current Month Budget Status ---");
        System.out.printf("  Monthly Budget          : Rs. %.2f%n", budget);
        System.out.printf("  Spent This Month        : Rs. %.2f%n", spent);
        System.out.printf("  Remaining Budget        : Rs. %.2f%n", remaining);
        System.out.printf("  Budget Usage Percentage : %.2f%%%n", usagePercent);

        // Rule-based budget warnings
        if (spent.compareTo(budget) > 0) {
            System.out.printf("[!] ALERT: You have exceeded your monthly budget by Rs. %.2f!%n", spent.subtract(budget));
        } else if (usagePercent.compareTo(BigDecimal.valueOf(80)) >= 0) {
            System.out.printf("[!] WARNING: You have spent %.2f%% of your budget (>= 80%% threshold).%n", usagePercent);
        } else {
            System.out.println("[i] You are safely within your budget limit.");
        }
    }

    /**
     * Feature 6: Simple Affordability Checker.
     */
    private void handleCheckAffordability() {
        System.out.println("\n--- Purchase Affordability Checker ---");
        BigDecimal purchaseCost = readPositiveBigDecimal("Enter the cost of the planned purchase (Rs.): ");

        BigDecimal availableBalance = financeService.getAvailableBalance();
        YearMonth currentMonth = YearMonth.now();
        BigDecimal spentThisMonth = financeService.getMonthExpenses(currentMonth);

        BudgetService.AffordabilityReport report = budgetService.evaluateAffordability(
                purchaseCost, availableBalance, spentThisMonth
        );

        System.out.println("\n----------------- Affordability Analysis -----------------");
        System.out.printf("Current Available Balance          : Rs. %.2f%n", report.getCurrentBalance());
        System.out.printf("Planned Purchase Cost              : Rs. %.2f%n", report.getPurchaseCost());
        System.out.printf("Estimated Balance After Purchase   : Rs. %.2f%n", report.getEstimatedBalanceAfter());

        if (report.isBudgetConfigured()) {
            System.out.printf("Remaining Monthly Budget (Before)  : Rs. %.2f%n", report.getRemainingBudgetBefore());
            System.out.printf("Remaining Monthly Budget (After)   : Rs. %.2f%n", report.getEstimatedRemainingBudgetAfter());
        } else {
            System.out.println("Monthly Budget                     : [Not set yet - consider setting one in Option 5]");
        }

        System.out.println("\nExplanation & Insight:");
        System.out.println(report.getAssessment());
        System.out.println("----------------------------------------------------------");
    }

    /**
     * Feature 7: Spending Insights (Rule-based, no ML/AI).
     */
    private void handleViewSpendingInsights() {
        System.out.println("\n--- Spending Insights ---");
        List<String> insights = financeService.getSpendingInsights(budgetService);
        if (insights.isEmpty()) {
            System.out.println("- Keep recording your income and expenses to unlock deeper financial insights.");
        } else {
            for (String insight : insights) {
                System.out.println("- " + insight);
            }
        }
    }

    // =========================================================================
    // Safe Console Input & Validation Helpers
    // =========================================================================

    /**
     * Reads a menu choice integer safely.
     */
    private int readMenuChoice() {
        String input = scanner.nextLine().trim();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Reads a non-empty string input.
     */
    private String readNonEmptyString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("[!] Input cannot be empty. Please try again.");
        }
    }

    /**
     * Reads a strictly positive BigDecimal amount.
     */
    private BigDecimal readPositiveBigDecimal(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                BigDecimal val = new BigDecimal(input);
                if (val.compareTo(BigDecimal.ZERO) > 0) {
                    return val.setScale(2, java.math.RoundingMode.HALF_UP);
                }
                System.out.println("[!] Amount must be greater than zero. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("[!] Invalid number format. Please enter a valid monetary amount (e.g., 2500 or 150.50).");
            }
        }
    }

    /**
     * Displays options and returns the chosen item string.
     */
    private String selectPredefinedOption(String prompt, String[] options) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= options.length) {
                    return options[choice - 1];
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("[!] Please enter a valid option number between 1 and %d.%n", options.length);
        }
    }

    /**
     * Reads a date or defaults to specified default date if blank.
     */
    private LocalDate readDateOrDefault(String prompt, LocalDate defaultDate) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return defaultDate;
            }
            try {
                return LocalDate.parse(input, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("[!] Invalid date format. Please use yyyy-MM-dd (e.g., 2026-10-12) or press Enter for today.");
            }
        }
    }

    /**
     * Truncates strings for neat table display.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
