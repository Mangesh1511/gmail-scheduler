FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY mail.ai/server/pom.xml ./pom.xml
COPY mail.ai/server/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/mail-ai-server-0.1.0-SNAPSHOT.jar ./app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
