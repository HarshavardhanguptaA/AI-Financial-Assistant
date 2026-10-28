package com.financialcopilot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single financial transaction (Income or Expense).
 */
public class Transaction {
    private final int id;
    private final TransactionType type;
    private final String description;
    private final BigDecimal amount;
    private final String categoryOrSource;
    private final LocalDate date;

    public Transaction(int id, TransactionType type, String description, BigDecimal amount, String categoryOrSource, LocalDate date) {
        if (id <= 0) {
            throw new IllegalArgumentException("Transaction ID must be positive.");
        }
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

        this.id = id;
        this.type = type;
        this.description = description.trim();
        this.amount = amount;
        this.categoryOrSource = categoryOrSource.trim();
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategoryOrSource() {
        return categoryOrSource;
    }

    public LocalDate getDate() {
        return date;
    }

    @Override
    public String toString() {
        return String.format("Transaction #%d [%s] %s - %s: Rs. %.2f on %s",
                id, type, description, categoryOrSource, amount, date);
    }
}
