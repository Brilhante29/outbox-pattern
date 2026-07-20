FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon 2>&1 || true
COPY src ./src
RUN gradle test --no-daemon 2>&1 || true
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
