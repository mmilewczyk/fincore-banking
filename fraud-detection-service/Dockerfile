# Multi-stage Dockerfile - shared by all FinCore services.
#
# STAGE 1 - builder: compiles the JAR using Maven
# STAGE 2 - runtime: minimal JRE image, no build tools
#
# Layer ordering is intentional:
#   1. Dependencies layer  - changes rarely, cached aggressively
#   2. Application layer   - changes on every commit, always rebuilt
#
# Build args:
#   SERVICE_PORT - the port the service listens on (injected by CI matrix)

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy parent POM first - enables dependency caching across all service builds
COPY pom.xml .

# Download dependencies before copying source - cache layer survives source changes
RUN mvn dependency:go-offline -q --no-transfer-progress 2>/dev/null || true

# Copy source
COPY src ./src

# Build JAR - tests already ran in CI, skip here
RUN mvn package -DskipTests -q --no-transfer-progress

# Extract Spring Boot layered JAR for better layer caching at runtime
# Layers: dependencies (stable) / spring-boot-loader / snapshot-dependencies / application (changes often)
RUN java -Djarmode=layertools -jar target/*.jar extract --destination /build/layers

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S fincore && adduser -S fincore -G fincore
USER fincore

WORKDIR /app

# Copy extracted layers in order of change frequency (least -> most)
# Docker caches each COPY as a separate layer - only the changed layers are pushed
COPY --from=builder /build/layers/dependencies/          ./
COPY --from=builder /build/layers/spring-boot-loader/    ./
COPY --from=builder /build/layers/snapshot-dependencies/ ./
COPY --from=builder /build/layers/application/           ./

ARG SERVICE_PORT=8080
ENV SERVER_PORT=${SERVICE_PORT}

# JVM tuning for containers:
#   UseContainerSupport - respects cgroup memory limits (default in JDK 11+, explicit here for clarity)
#   MaxRAMPercentage   - use 75% of container memory for heap (leaves room for off-heap, metaspace)
#   ExitOnOutOfMemoryError - crash fast and let K8s restart rather than limp along
#   G1GC               - default in JDK 9+, good for containerized latency-sensitive workloads
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE ${SERVICE_PORT}

# Health check for Docker Compose and local testing
# K8s uses its own liveness/readiness probes - this is for docker-compose only
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:${SERVICE_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher"]