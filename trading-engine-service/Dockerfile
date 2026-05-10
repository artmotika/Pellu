FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY trading-engine-service/build/libs/trading-engine-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
