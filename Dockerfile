# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build/backend
COPY backend/pom.xml backend/mvnw ./
COPY backend/.mvn/ .mvn/
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY backend/src/main/ src/main/
COPY frontend/ /build/frontend/
# SQL/security tests run separately against PostgreSQL; no test database in this image.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -Dmaven.test.skip=true package
COPY docker/Healthcheck.java /build/Healthcheck.java
RUN javac -d /build/health /build/Healthcheck.java

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /opt/cap
COPY --from=build --chown=10001:10001 /build/backend/target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build --chown=10001:10001 /build/health/ health/
USER 10001:10001
EXPOSE 8081
HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=3 CMD ["java", "-cp", "/opt/cap/health", "Healthcheck"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/opt/cap/app.jar"]
