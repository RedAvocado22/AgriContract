FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./pom.xml
COPY escrow-service/pom.xml ./escrow-service/pom.xml
RUN mvn dependency:go-offline -pl escrow-service -q
COPY escrow-service/src ./escrow-service/src
RUN mvn clean package -pl escrow-service -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/escrow-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
