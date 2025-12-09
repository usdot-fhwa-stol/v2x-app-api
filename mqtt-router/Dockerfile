FROM gradle:8.10-jdk22 AS build
WORKDIR /app

# Copy build files
COPY mqtt-router/build.gradle mqtt-router/settings.gradle ./mqtt-router/
COPY mqtt-router/src ./mqtt-router/src

# Build the application
WORKDIR /app/mqtt-router
RUN gradle clean build -x test --no-daemon

FROM eclipse-temurin:22-jre-alpine
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/mqtt-router/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

