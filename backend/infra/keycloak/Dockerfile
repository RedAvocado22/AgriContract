# Keycloak with the AgriContract registration-role provider (D47). The provider is built here so the stack needs
# no local Maven; it is not part of the backend reactor.
FROM maven:3.9-eclipse-temurin-17 AS spi
WORKDIR /build
COPY role-spi/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY role-spi/src src
RUN mvn -q -B package

FROM quay.io/keycloak/keycloak:24.0.4
COPY --from=spi /build/target/agricontract-keycloak-role-spi.jar /opt/keycloak/providers/
