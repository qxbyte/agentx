FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY agentx-common/pom.xml agentx-common/
COPY agentx-infra-ai/pom.xml agentx-infra-ai/
COPY agentx-auth/pom.xml agentx-auth/
COPY agentx-server/pom.xml agentx-server/
RUN mvn -q -B dependency:go-offline || true
COPY . .
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/agentx-server/target/agentx-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
