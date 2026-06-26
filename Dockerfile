# ---- Build stage ----
FROM gradle:8.11-jdk21 AS build

WORKDIR /app

COPY . .

RUN gradle :server:clean :server:shadowJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/server/build/libs/server-all.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]