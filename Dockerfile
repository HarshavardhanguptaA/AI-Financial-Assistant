# Multi-stage Dockerfile for AI Financial Copilot Backend
# Stage 1: Build stage with Maven and Java 17
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom.xml and source directory
COPY pom.xml .
COPY src ./src

# Build package and copy runtime dependencies to target/dependency
RUN mvn clean package -DskipTests

# Stage 2: Minimal Java 17 runtime container
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create directory for persistent SQLite data
RUN mkdir -p /var/data && chmod 777 /var/data

# Copy compiled classes and copied runtime dependencies
COPY --from=builder /app/target/classes ./classes
COPY --from=builder /app/target/dependency ./lib

# Copy static frontend assets (so WebServer can serve them if accessed directly)
COPY public ./public

# Environment configurations
ENV PORT=8080
ENV DB_PATH=/var/data/financial_copilot.db

# Expose default HTTP port
EXPOSE 8080

# Launch WebServer
CMD ["java", "-cp", "classes:lib/*", "com.financialcopilot.WebServer"]

