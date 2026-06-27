FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./pom.xml
COPY product-service/pom.xml ./product-service/pom.xml
RUN mvn dependency:go-offline -pl product-service -q
COPY product-service/src ./product-service/src
RUN mvn clean package -pl product-service -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/product-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
