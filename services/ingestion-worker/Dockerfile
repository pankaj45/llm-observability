FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml pom.xml
COPY services/inference-gateway/pom.xml services/inference-gateway/pom.xml
COPY services/ingestion-worker/pom.xml services/ingestion-worker/pom.xml
COPY services/analytics-query/pom.xml services/analytics-query/pom.xml
COPY services/ingestion-worker/src services/ingestion-worker/src

RUN mvn -pl services/ingestion-worker -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=builder /workspace/services/ingestion-worker/target/ingestion-worker-*.jar app.jar

USER app
EXPOSE 8082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
