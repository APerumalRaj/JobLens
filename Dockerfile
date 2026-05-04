# Multi-stage build
FROM maven:3.8.1-openjdk-21 AS builder

WORKDIR /app

# Copy pom.xml and build dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src src
RUN mvn clean package -DskipTests

# Runtime image
FROM openjdk:21-slim

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/job-email-filter-1.0.0.jar app.jar

# Create tokens directory for OAuth tokens
RUN mkdir -p /app/tokens

# Volume for token storage (persistent across restarts)
VOLUME ["/app/tokens"]

# Health check
HEALTHCHECK --interval=60s --timeout=10s --start-period=30s --retries=3 \
    CMD java -cp app.jar org.springframework.boot.loader.JarLauncher -c "health"

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
