
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-alpine
WORKDIR /app

RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

COPY --from=build /app/target/*.jar app.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]