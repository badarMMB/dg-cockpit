# ── Stage 1 : Build Angular ───────────────────────────────────────────────────
FROM node:20-alpine AS ng-build
WORKDIR /workspace
COPY package*.json ./
RUN npm ci --prefer-offline
COPY . .
RUN npx ng build --configuration production

# ── Stage 2 : Build Spring Boot ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS mvn-build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
RUN mvn dependency:resolve -DincludeScope=runtime -B || true
COPY backend/src ./src
COPY --from=ng-build /workspace/backend/src/main/resources/static ./src/main/resources/static
RUN mvn package -DskipTests -B

# ── Stage 3 : Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=mvn-build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
