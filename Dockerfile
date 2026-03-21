# Multi-stage build for PestScout backend
# Stage 1: build the executable jar
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Install dependencies using the Gradle wrapper
COPY gradlew ./
COPY gradle/wrapper ./gradle/wrapper
RUN chmod +x gradlew
COPY build.gradle settings.gradle ./
COPY src ./src
COPY docs ./docs

# Build the Spring Boot fat jar
RUN ./gradlew bootJar --no-daemon

# Stage 2: create a lightweight runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built jar
COPY --from=build /app/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Default to the production profile and inject infrastructure values at runtime.
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java","-jar","/app/app.jar"]
