package com.financialcopilot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles SQLite database connections, initialization, and schema management.
 */
public class DatabaseManager {
    public static final String DEFAULT_DB_NAME = "financial_copilot.db";
    private final String dbUrl;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[Database Error] SQLite JDBC Driver not found in classpath: " + e.getMessage());
        }
    }

    public DatabaseManager() {
        this(DEFAULT_DB_NAME);
    }

    public DatabaseManager(String dbFileName) {
        if (dbFileName != null) {
            java.io.File file = new java.io.File(dbFileName);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }
        this.dbUrl = "jdbc:sqlite:" + dbFileName;
        initializeDatabase();
    }

    /**
     * Explicit static factory or direct constructor.
     */
    public static DatabaseManager forFile(String dbFileName) {
        return new DatabaseManager(dbFileName);
    }

    /**
     * Opens and returns a new JDBC connection to the SQLite database.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /**
     * Initializes tables in SQLite if they do not already exist.
     */
    public final void initializeDatabase() {
        String createTransactionsTable = """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                amount TEXT NOT NULL,
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                transaction_date TEXT NOT NULL
            );
            """;

        String createBudgetsTable = """
            CREATE TABLE IF NOT EXISTS monthly_budgets (
                month TEXT PRIMARY KEY,
                budget_amount TEXT NOT NULL
            );
            """;

        String createDateIndex = """
            CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions (transaction_date);
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Enable WAL mode and busy timeout for performance and multi-access safety
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute(createTransactionsTable);
            stmt.execute(createBudgetsTable);
            stmt.execute(createDateIndex);
        } catch (SQLException e) {
            System.err.println("[Database Error] Failed to initialize database: " + e.getMessage());
        }
    }

    public String getDbUrl() {
        return dbUrl;
    }
}
