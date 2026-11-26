# syntax=docker/dockerfile:1.7
# Build context: repository root  (docker compose build processing-service)

# ---------- 1. build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# POMs first: this layer only changes when dependencies change
COPY pom.xml ./
COPY common/pom.xml common/
COPY transaction-service/pom.xml transaction-service/
COPY processing-service/pom.xml processing-service/
COPY search-service/pom.xml search-service/
COPY e2e-tests/pom.xml e2e-tests/

COPY common/src common/src
COPY processing-service/src processing-service/src

# the cache mount keeps ~/.m2 between builds (and between the three services)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl processing-service -am package -DskipTests \
 && cp processing-service/target/processing-service-*-exec.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------- 2. runtime ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# layered copy: dependencies rarely change, application classes often
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8082
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=10 \
    CMD wget -qO- http://localhost:8082/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
