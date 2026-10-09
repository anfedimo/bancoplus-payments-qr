FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Runtime mínimo: Alpine sin herramientas adicionales de la imagen base Ubuntu
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/target/payments-qr-1.0.0.jar app.jar
RUN adduser -D -u 10001 app
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
