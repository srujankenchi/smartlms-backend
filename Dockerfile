# ── Stage 1: Build the Spring Boot application ──
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests

# ── Stage 2: Minimal runtime image ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create upload folders for runtime file attachments
RUN mkdir -p uploads/chat uploads/submissions

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Render exposes PORT environment variable (defaults to 8008 locally)
ENV PORT=8008
EXPOSE 8008

# Run Spring Boot app
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
