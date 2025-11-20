# === Build image for Java ===
FROM gradle:jdk22 AS builder

WORKDIR /home/app

# jpo-asn-pojos files
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

# j2735-2024-ffm-lib files
COPY ./j2735-ffm-java/j2735-2024-ffm-lib/src /home/app/j2735-ffm-java/j2735-2024-ffm-lib/src
COPY ./j2735-ffm-java/j2735-2024-ffm-lib/build.gradle /home/app/j2735-ffm-java/j2735-2024-ffm-lib

# Copy V2X App API files
COPY ./v2x-app-api/build.gradle ./v2x-app-api/settings.gradle ./v2x-app-api/

# Copy source code
COPY ./v2x-app-api/src ./v2x-app-api/src

# Build the application
RUN cd v2x-app-api && gradle build -x test

# === Runtime image ===
FROM eclipse-temurin:22-jdk-noble

WORKDIR /home/app

# Install native library
COPY ./j2735-ffm-java/lib/libasnapplication.so /usr/lib/

# Copy the built application and dependencies
COPY --from=builder /home/app/v2x-app-api/build/libs/*.jar ./app.jar
COPY --from=builder /home/app/v2x-app-api/src/main/resources/application.yml ./

COPY ./tim_config_files/tim-config.json /tim_config_files/tim-config.json
COPY ./tim_config_files/tim-icons /tim_config_files/tim-icons

ENTRYPOINT ["java", "-Djava.rmi.server.hostname=$DOCKER_HOST_IP", "--enable-native-access=ALL-UNNAMED", "-jar", "/home/app/app.jar"]