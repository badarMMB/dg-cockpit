# Stage 1 — compile Java backend (Angular static already built into src/main/resources/static)
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY backend/pom.xml .
RUN mvn dependency:go-offline -q
COPY backend/src ./src
RUN mvn clean package -DskipTests -q

# Stage 2 — runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/dg-cockpit-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
