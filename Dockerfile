# Stage 1: Build the JAR inside the container
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /app

# Limit Maven heap memory and optimize compilation for container build
ENV MAVEN_OPTS="-Xmx512m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

# Copy Maven wrapper and POM
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings from Windows CRLF to Linux LF and set executable permissions
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Copy source code and build package cleanly without progress bar flooding
COPY src/ ./src/
RUN ./mvnw clean package -B --no-transfer-progress -DskipTests

# Stage 2: Minimal runtime image for Render
FROM eclipse-temurin:24-jre
WORKDIR /app

COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

