FROM gradle:8.12-jdk21 AS build
WORKDIR /app
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY gradle.lockfile build.gradle.kts settings.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --configuration runtimeClasspath --no-daemon
COPY src ./src
COPY contracts ./contracts
COPY .portfolio/contracts ./.portfolio/contracts
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle test bootJar -PexcludeIntegration --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
COPY contracts ./contracts
COPY .portfolio ./.portfolio
COPY gradle.lockfile ./gradle.lockfile
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
