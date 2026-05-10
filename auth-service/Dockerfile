FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY auth-service/build/libs/auth-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
