FROM maven:3.9.16-eclipse-temurin-17-noble AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:17-jre-noble

RUN groupadd --system limitforge \
    && useradd --system --gid limitforge --home-dir /app --shell /usr/sbin/nologin limitforge
WORKDIR /app
COPY --from=build --chown=limitforge:limitforge \
    /workspace/target/limitforge-engine-1.0.0.jar \
    /app/limitforge.jar

USER limitforge
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD ["java", "-cp", "/app/limitforge.jar", "com.trading.api.ApiHealthProbe"]

ENTRYPOINT ["java", "-Dapi.bind=0.0.0.0", "-Dlogback.configurationFile=logback-container.xml", "-cp", "/app/limitforge.jar", "com.trading.api.SimulationServer"]
CMD ["8080"]
