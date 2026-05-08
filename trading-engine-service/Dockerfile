FROM eclipse-temurin:21-jdk-alpine as build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew
RUN ./gradlew :trading-engine-service:build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/trading-engine-service/build/libs/trading-engine-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
