# syntax=docker/dockerfile:1.7

# Stage 1: build the Spring Boot fat jar with the Maven Wrapper.
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Copy wrapper + pom first so dependency resolution caches when only
# application source changes. The `go-offline` step still hits the
# network if pom.xml changes; that's intentional.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
# Skip tests in the image build. CI runs them separately with the full
# Testcontainers + WireMock stack, so the jar build can stay fast.
RUN ./mvnw -B -q -DskipTests package

# Stage 2: slim runtime, just the jar and a non-root user.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
# `prod` profile flips JPA to `validate`, enables Flyway, reads DB + JWT
# secrets from env. See application-prod.yml.
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
