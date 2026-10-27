FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./pom.xml
COPY shared/java-eventing/pom.xml ./shared/java-eventing/pom.xml
COPY shared/java-web-error/pom.xml ./shared/java-web-error/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY user-service/pom.xml ./user-service/pom.xml
COPY product-service/pom.xml ./product-service/pom.xml
COPY contract-service/pom.xml ./contract-service/pom.xml
COPY escrow-service/pom.xml ./escrow-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
COPY reputation-service/pom.xml ./reputation-service/pom.xml
COPY inspection-service/pom.xml ./inspection-service/pom.xml
COPY bank-service/pom.xml ./bank-service/pom.xml
RUN mvn dependency:go-offline -pl user-service -am -q
# Shared library modules the service may depend on; -am builds them in the same reactor.
COPY shared/java-eventing/src ./shared/java-eventing/src
COPY shared/java-web-error/src ./shared/java-web-error/src
COPY user-service/src ./user-service/src
RUN mvn clean package -pl user-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/user-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
