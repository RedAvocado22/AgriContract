FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY user-service/pom.xml ./user-service/pom.xml
COPY product-service/pom.xml ./product-service/pom.xml
COPY contract-service/pom.xml ./contract-service/pom.xml
COPY escrow-service/pom.xml ./escrow-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
RUN mvn dependency:go-offline -pl contract-service -q
COPY contract-service/src ./contract-service/src
RUN mvn clean package -pl contract-service -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/contract-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
