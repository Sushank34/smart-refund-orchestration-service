# --- Build stage: compile and package the jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# --- Run stage: slim JRE, just the jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# The app reads $PORT at runtime (Render/Cloud Run inject it); 8085 is the local default.
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
