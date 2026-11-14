# SmartDesk - backend Spring Boot
# --- Compilation ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

# --- Exécution ---
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home app \
    && mkdir -p /data/documents && chown -R app /data
WORKDIR /app
COPY --from=build /src/target/backend-1.0.0.jar app.jar
USER app
ENV SERVER_PORT=8095 \
    DOCUMENTS_DIR=/data/documents \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8095
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
    CMD curl -fs http://localhost:8095/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
