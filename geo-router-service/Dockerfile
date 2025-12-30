# Build stage
FROM gradle:jdk22 AS builder

WORKDIR /home/app

# Copy jpo-asn-pojos files
COPY ./jpo-asn-pojos/jpo-asn-runtime/build.gradle ./jpo-asn-pojos/jpo-asn-runtime/build.gradle
COPY ./jpo-asn-pojos/jpo-asn-runtime/settings.gradle ./jpo-asn-pojos/jpo-asn-runtime/settings.gradle
COPY ./jpo-asn-pojos/jpo-asn-runtime/gradle ./jpo-asn-pojos/jpo-asn-runtime/gradle
COPY ./jpo-asn-pojos/jpo-asn-runtime/src ./jpo-asn-pojos/jpo-asn-runtime/src

COPY ./jpo-asn-pojos/jpo-asn-j2735-2024/build.gradle ./jpo-asn-pojos/jpo-asn-j2735-2024/build.gradle
COPY ./jpo-asn-pojos/jpo-asn-j2735-2024/settings.gradle ./jpo-asn-pojos/jpo-asn-j2735-2024/settings.gradle
COPY ./jpo-asn-pojos/jpo-asn-j2735-2024/gradle ./jpo-asn-pojos/jpo-asn-j2735-2024/gradle
COPY ./jpo-asn-pojos/jpo-asn-j2735-2024/src ./jpo-asn-pojos/jpo-asn-j2735-2024/src

# Build jpo-asn-pojos
RUN cd jpo-asn-pojos/jpo-asn-j2735-2024 && gradle clean build -x test

# Copy j2735-2024-ffm-lib files
COPY ./j2735-ffm-java/j2735-2024-ffm-lib/src /home/app/j2735-ffm-java/j2735-2024-ffm-lib/src
COPY ./j2735-ffm-java/j2735-2024-ffm-lib/build.gradle /home/app/j2735-ffm-java/j2735-2024-ffm-lib/build.gradle
COPY ./j2735-ffm-java/j2735-2024-ffm-lib/settings.gradle /home/app/j2735-ffm-java/j2735-2024-ffm-lib/settings.gradle

# Copy geo-router-service files
COPY ./geo-router-service/build.gradle ./geo-router-service/settings.gradle ./geo-router-service/
COPY ./geo-router-service/src ./geo-router-service/src

# Build the application
RUN cd geo-router-service && gradle clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:22-jre-jammy

WORKDIR /app

# Install native library
COPY ./j2735-ffm-java/lib/libasnapplication.so /usr/lib/

# Copy the built JAR
COPY --from=builder /home/app/geo-router-service/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]

