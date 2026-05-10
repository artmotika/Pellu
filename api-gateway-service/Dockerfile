FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY api-gateway-service/build/libs/api-gateway-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
