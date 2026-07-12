FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -q -B -pl agentx-mcp-server -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/agentx-mcp-server/target/agentx-mcp-server-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
