# AI Financial Copilot for First-Time Earners

**AI Financial Copilot** is a lightweight personal finance tracking and budgeting system built for first-time earners. It features:
- Core Java backend with **SQLite JDBC persistence**.
- Built-in lightweight HTTP web server using Java standard library (`com.sun.net.httpserver.HttpServer`).
- Modern, clean, minimal fintech web dashboard powered by HTML5, CSS3, and Vanilla JavaScript.
- Interactive console application alternative with zero dependencies beyond SQLite JDBC.
- Strict monetary precision using `java.math.BigDecimal` (no floating-point rounding errors).

---

## Architecture Overview

```
javaminipjtttt/
├── pom.xml                                  # Maven build descriptor (Java 17+, sqlite-jdbc, gson, slf4j)
├── lib/                                     # Standalone dependencies for javac/java
│   ├── sqlite-jdbc-3.45.2.0.jar
│   ├── gson-2.10.1.jar
│   ├── slf4j-api-2.0.12.jar
│   └── slf4j-nop-2.0.12.jar
├── public/                                  # Modern Web Dashboard (Vanilla HTML5 / CSS3 / JS)
│   ├── index.html                           # Semantic layout, overview cards, forms, tables
│   ├── styles.css                           # Minimal fintech design system (slate, neutral, accents)
│   └── app.js                              # REST API client, DOM rendering, form validation
├── src/
│   └── com/financialcopilot/
│       ├── DatabaseManager.java             # SQLite connection, DDL, WAL mode, indexing
│       ├── TransactionType.java             # INCOME / EXPENSE enum
│       ├── Transaction.java                 # Immutable transaction model with validation
│       ├── FinanceService.java              # SQLite transaction persistence, totals & insights
│       ├── BudgetService.java               # SQLite monthly budget management & affordability
│       ├── WebServer.java                   # Built-in HTTP server with REST APIs & static file hosting
│       ├── Main.java                        # Interactive console CLI application
│       ├── Phase2TestRunner.java            # Automated persistence & restart regression tests
│       └── Phase3TestRunner.java            # Automated Web Server & REST API test runner
├── bin/                                     # Compiled class files
└── financial_copilot.db                     # Local SQLite database (auto-created on startup)
```

---

## Quick Start

### 1. Compile the Project

You can compile using `javac` directly:
```powershell
javac -cp "lib/*" -d bin src/com/financialcopilot/*.java
```

Or using Maven:
```powershell
mvn compile
```

---

### 2. Launch the Web Dashboard

Run the lightweight HTTP web server:
```powershell
java -cp "bin;lib/*" com.financialcopilot.WebServer
```
*(By default, binds to port `8080`. To customize port or database: `java -cp "bin;lib/*" com.financialcopilot.WebServer <port> <databaseFile>`)*

Once started, open your web browser at:
```
http://localhost:8080
```

---

### 3. Launch the Console Application (Alternative)

To use the interactive CLI interface instead of the web dashboard:
```powershell
java -cp "bin;lib/*" com.financialcopilot.Main
```

---

### 4. Run Automated Test Suites

- **Run Phase 3 Web Server & REST API Tests:**
  ```powershell
  java -cp "bin;lib/*" com.financialcopilot.Phase3TestRunner
  ```
  *(Verifies static file hosting, all 7 REST API endpoints, JSON serialization, boundary validations, HTTP status codes, and server restart persistence against an isolated test database.)*

- **Run Phase 2 Persistence & Regression Tests:**
  ```powershell
  java -cp "bin;lib/*" com.financialcopilot.Phase2TestRunner
  ```

---

## REST API Reference

All API requests and responses use `application/json; charset=UTF-8` and standard HTTP status codes.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` or `/index.html` | Serves dashboard frontend |
| `GET` | `/styles.css`, `/app.js` | Serves static assets |
| `GET` | `/api/summary` | Returns all-time totals, current month totals, remaining budget, usage %, and category breakdown |
| `GET` | `/api/transactions` | Returns transaction history list |
| `POST` | `/api/transactions` | Records a new income or expense transaction |
| `GET` | `/api/budget` | Returns current month budget status and usage |
| `PUT` | `/api/budget` | Sets or updates the monthly spending budget in SQLite |
| `POST` | `/api/affordability` | Evaluates affordability for a planned purchase amount |
| `GET` | `/api/insights` | Returns rule-based spending heuristics |

### Example Payloads

#### `POST /api/transactions`
```json
{
  "description": "Monthly Stipend",
  "amount": "15000.00",
  "type": "INCOME",
  "category": "Stipend",
  "date": "2026-10-12"
}
```

#### `PUT /api/budget`
```json
{
  "amount": "5000.00"
}
```

#### `POST /api/affordability`
```json
{
  "amount": "2000.00"
}
```

---

## Key Design Principles
1. **Zero External Server Overhead**: Uses Java's built-in `com.sun.net.httpserver.HttpServer` without heavyweight dependencies like Spring Boot or Node.js.
2. **Precision Finance**: All monetary math is handled in Java using `BigDecimal` and stored as exact strings in SQLite.
3. **Dual Access**: The same underlying SQLite database and business logic are shared seamlessly between the console application and the web dashboard.
4. **Clean Fintech UI**: Minimalist aesthetic with high contrast, clear information hierarchy, generous whitespace, and responsive design for mobile and desktop.
