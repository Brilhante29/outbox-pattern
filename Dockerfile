FROM gradle:8.12-jdk21 AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew gradlew.bat gradle.lockfile build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew dependencies --configuration runtimeClasspath --no-daemon
COPY src ./src
COPY contracts ./contracts
COPY .portfolio/contracts ./.portfolio/contracts
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew test bootJar -PexcludeIntegration --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
COPY contracts ./contracts
COPY .portfolio ./.portfolio
COPY gradle.lockfile ./gradle.lockfile
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
