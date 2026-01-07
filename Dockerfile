# Multi-stage build for PestScout backend
# Stage 1: build the executable jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Install dependencies using Gradle wrapper
COPY gradlew gradle ./
RUN chmod +x gradlew
COPY build.gradle settings.gradle ./
COPY src ./src
COPY docs ./docs

# Build the Spring Boot fat jar
RUN ./gradlew bootJar --no-daemon

# Stage 2: create a lightweight runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built jar
COPY --from=build /app/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Environment variables for overridable configuration
ENV SPRING_PROFILES_ACTIVE=prod \
    SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/pestscan_scouting" \
    SPRING_DATASOURCE_USERNAME=postgres \
    SPRING_DATASOURCE_PASSWORD=admin \
    SPRING_DATA_REDIS_HOST=localhost \
    SPRING_DATA_REDIS_PORT=6379

ENTRYPOINT ["java","-jar","/app/app.jar"]
