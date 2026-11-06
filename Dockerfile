# Stage 1: Build the JAR inside the container
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /app

# Limit Maven heap memory and optimize compilation for container build
ENV MAVEN_OPTS="-Xmx512m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

# Copy Maven wrapper and POMs (pom-monolith.xml = build del monolito, ver comentario en ese archivo)
COPY .mvn/ .mvn/
COPY mvnw pom.xml pom-monolith.xml ./

# Normalize line endings from Windows CRLF to Linux LF and set executable permissions
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Copy source code and build the monolith jar cleanly without progress bar flooding
# (-N: solo la raiz, sin los modulos de microservicios que aqui no existen)
COPY src/ ./src/
RUN ./mvnw -f pom-monolith.xml -N -B --no-transfer-progress -DskipTests clean package

# Stage 2: Minimal runtime image for Render
FROM eclipse-temurin:24-jre
WORKDIR /app

COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

