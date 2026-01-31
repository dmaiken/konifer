# ==========================================
# Build the Application (Gradle)
# ==========================================
FROM gradle:9.3.0-jdk25 AS app-build
WORKDIR /home/gradle/app
# Cache dependencies
COPY gradle gradle
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY service/build.gradle.kts service/
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :service:dependencies --no-daemon || true
# Build Jar
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :service:buildFatJar --no-daemon -x test

# ==========================================
# Build LibVips
# ==========================================
# We use the SAME base OS to ensure binary compatibility
FROM ubuntu:24.04 AS vips-build

# Install the compilers and dev tools needed for the script
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    pkg-config \
    glib-2.0-dev \
    libexpat1-dev \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Run install script, but force it to install to a clean directory we can copy
COPY scripts/install-vips.sh /install-vips.sh
RUN chmod +x /install-vips.sh && /install-vips.sh  --with-deps --cleanup

# ==========================================
# The Final Runtime (Clean)
# ==========================================
FROM ubuntu:24.04 AS runtime

# We DO NOT install build-essential or dev libs here. Only shared libs.
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    tini \
    ca-certificates \
    libexpat1 \
    # Add runtime versions of libraries you linked against (e.g. libjpeg-turbo8)
    && rm -rf /var/lib/apt/lists/*

# Install GraalVM 25 (Manual Download)
ARG GRAAL_VERSION=25.0.0
ARG GRAAL_URL=https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAAL_VERSION}/graalvm-community-jdk-${GRAAL_VERSION}_linux-x64_bin.tar.gz

RUN wget -q $GRAAL_URL -O graal.tar.gz \
    && mkdir -p /opt/java/graalvm \
    && tar -xzf graal.tar.gz -C /opt/java/graalvm --strip-components=1 \
    # Aggressive JDK Cleanup: Remove src.zip, man pages, and jmods to save ~100MB
    && rm graal.tar.gz \
    && rm -f /opt/java/graalvm/lib/src.zip \
    && rm -rf /opt/java/graalvm/man \
    && rm -rf /opt/java/graalvm/jmods

ENV JAVA_HOME=/opt/java/graalvm
ENV PATH=$JAVA_HOME/bin:$PATH

# Copy Vips from Builder
# copy ONLY the compiled binaries.
COPY --from=vips-build /usr/local /usr/local
RUN ldconfig

RUN groupadd -r konifer && useradd -r -g konifer konifer

WORKDIR /app
RUN mkdir -p /app/config /app/tmp /app/logs
COPY --from=app-build /home/gradle/app/service/build/libs/*.jar konifer.jar
RUN chown -R konifer:konifer /app

USER konifer

## Necessary for jemalloc
ENV LD_PRELOAD="/usr/local/lib/libjemalloc.so"

ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java --enable-native-access=ALL-UNNAMED -XX:+UseCompactObjectHeaders -Djava.io.tmpdir=/app/tmp $JAVA_OPTS -jar konifer.jar -config=application.conf -config=/app/config/konifer.conf"]