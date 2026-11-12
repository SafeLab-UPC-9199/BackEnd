FROM eclipse-temurin:24-jdk
WORKDIR /app
# The executable jar is the *-exec.jar (classifier keeps the plain main jar
# for the modules that compile against this one)
COPY target/*-exec.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-jar","app.jar"]
