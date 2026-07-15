FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package


FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 appgroup \
    && useradd \
        --system \
        --uid 10001 \
        --gid appgroup \
        --create-home \
        --home-dir /home/appuser \
        --shell /usr/sbin/nologin \
        appuser

WORKDIR /app

COPY --from=build /workspace/target/voice-agent-service-*.jar /app/app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
