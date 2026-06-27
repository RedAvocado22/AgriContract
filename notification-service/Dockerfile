FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
RUN mvn dependency:go-offline -pl notification-service -q
COPY notification-service/src ./notification-service/src
RUN mvn clean package -pl notification-service -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/notification-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
