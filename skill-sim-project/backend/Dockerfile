# Build stage
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy dependency descriptors first for better caching
COPY pom.xml .

# Pre-fetch dependencies (cached between builds)
RUN mvn -B dependency:go-offline

# Copy source
COPY src ./src

# Build application
RUN mvn -B clean package -DskipTests

# --------------------------------------------------------
# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy packaged jar
COPY --from=build /app/target/*.jar app.jar

# Ensure SQLite file is created in a writable path
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/app/simulator.db

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
