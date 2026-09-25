# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer caching — these rarely change)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline --batch-mode

# Copy source code and build the JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests --batch-mode

# ---- Stage 2: Run ----
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Security: run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose default port (can be overridden by PORT env var)
EXPOSE 9090

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:${PORT:-9090}/actuator/health || exit 1

# JVM flags optimized for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
