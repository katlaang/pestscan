# Stage 1: Build the executable jar
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

# Stage 2: Create a lightweight runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built jar securely
COPY --from=build /app/build/libs/*.jar app.jar

# Add a tiny wait-for-db helper and install the Postgres client so pg_isready is available
COPY docker/wait-for-db.sh /wait-for-db.sh
RUN chmod +x /wait-for-db.sh \
	&& apt-get update \
	&& apt-get install -y postgresql-client \
	&& rm -rf /var/lib/apt/lists/*

EXPOSE 8080

# Default profile initialization configuration
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["/wait-for-db.sh","java","-jar","/app/app.jar"]
