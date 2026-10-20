# --- Build stage ---
FROM eclipse-temurin:27-jdk AS build
WORKDIR /workspace

# Copy the Gradle wrapper and build scripts first so dependency downloads are cached
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -q

# --- Runtime stage ---
FROM eclipse-temurin:27-jre
WORKDIR /app

RUN useradd --system --no-create-home app
USER app

COPY --from=build /workspace/build/libs/data-processing-service-*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
