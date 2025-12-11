# Build stage
FROM gradle:jdk22 AS builder

# Install Java 21 for jpo-asn-pojos dependencies
# The jpo-asn-runtime and jpo-asn-j2735-2024 projects require Java 21
# Download and install Java 21 to /usr/lib/jvm where Gradle auto-detection will find it
RUN apt-get update && \
    apt-get install -y wget ca-certificates && \
    mkdir -p /usr/lib/jvm && \
    cd /tmp && \
    wget -q https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9+10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz && \
    tar -xzf OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz -C /usr/lib/jvm && \
    mv /usr/lib/jvm/jdk-21.0.9+10 /usr/lib/jvm/java-21-temurin && \
    rm OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz && \
    apt-get remove -y wget && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Enable Gradle toolchain auto-provisioning as fallback
ENV GRADLE_OPTS="-Dorg.gradle.java.installations.auto-download=true"

WORKDIR /build

# Copy required dependencies (build context is set to project root in docker-compose)
# Copy the extension project
COPY hivemq-v2x-routing-extension/ ./hivemq-v2x-routing-extension/

# Copy required Gradle composite build dependencies
COPY jpo-asn-pojos/ ./jpo-asn-pojos/
COPY j2735-ffm-java/ ./j2735-ffm-java/

# Copy native library if not already in resources
# The library should be in src/main/resources/j2735ffm/libasnapplication.so
# If it's not there, copy it from j2735-ffm-java
RUN if [ ! -f hivemq-v2x-routing-extension/src/main/resources/j2735ffm/libasnapplication.so ]; then \
    mkdir -p hivemq-v2x-routing-extension/src/main/resources/j2735ffm && \
    cp j2735-ffm-java/lib/libasnapplication.so hivemq-v2x-routing-extension/src/main/resources/j2735ffm/ || true; \
    fi

# Build the extension (from the extension directory)
WORKDIR /build/hivemq-v2x-routing-extension
RUN ./gradlew --no-daemon clean buildExtension

# Runtime stage
FROM hivemq/hivemq4:4.47.0

# Copy the built extension
COPY --from=builder /build/hivemq-v2x-routing-extension/build/distributions/v2x-geohash-routing-extension-*.zip /tmp/extension.zip

# Install the extension
USER root
# Install unzip if not available
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

# Extract extension and set permissions
# HiveMQ typically runs as UID 1000, but we'll use the current user's UID/GID
RUN unzip -q /tmp/extension.zip -d /opt/hivemq/extensions/v2x-geohash-routing && \
    rm /tmp/extension.zip && \
    # Ensure native library is executable
    chmod +x /opt/hivemq/extensions/v2x-geohash-routing/extension/lib/libasnapplication.so 2>/dev/null || true && \
    # Set ownership to match HiveMQ's default user (UID 1000)
    chown -R 1000:1000 /opt/hivemq/extensions/v2x-geohash-routing

# Create entrypoint script to inject debug options
USER root
RUN echo '#!/bin/bash\n\
# Add debug options from HIVEMQ_OPTS if set\n\
if [ -n "$HIVEMQ_OPTS" ]; then\n\
    export JAVA_OPTS="${JAVA_OPTS} ${HIVEMQ_OPTS}"\n\
fi\n\
# Call original HiveMQ entrypoint\n\
exec /opt/docker-entrypoint.sh "$@"' > /opt/hivemq-entrypoint-debug.sh && \
    chmod +x /opt/hivemq-entrypoint-debug.sh && \
    chown 1000:1000 /opt/hivemq-entrypoint-debug.sh

# Switch back to the default HiveMQ user (UID 1000)
USER 1000

# Override entrypoint to use our debug script
ENTRYPOINT ["/opt/hivemq-entrypoint-debug.sh"]
CMD ["/opt/hivemq/bin/run.sh"]

# Expose ports (including debug port)
EXPOSE 1883 8000 8888 5005

