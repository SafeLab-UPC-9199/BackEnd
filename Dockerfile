# Stage 1: Build the JAR inside the container
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and POM
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings from Windows CRLF to Linux LF and set executable permissions
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Copy source code and build package
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal runtime image for Render
FROM eclipse-temurin:24-jre
WORKDIR /app

COPY --from=builder /app/target/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
