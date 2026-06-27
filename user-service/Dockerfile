FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./pom.xml
COPY user-service/pom.xml ./user-service/pom.xml
RUN mvn dependency:go-offline -pl user-service -q
COPY user-service/src ./user-service/src
RUN mvn clean package -pl user-service -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/user-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
