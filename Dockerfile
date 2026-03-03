# ==========================================
# Build LibVips
# ==========================================
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

# Run install script, but force it to install to a clean directory
COPY scripts/install-vips.sh /install-vips.sh

# Refresh shared library cache
RUN ldconfig

RUN chmod +x /install-vips.sh && /install-vips.sh  --with-deps --cleanup

# ==========================================
# The Final Runtime (Clean)
# ==========================================
FROM ubuntu:24.04 AS runtime

## Generated from scripts/query-runtime-deps.sh
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libaom3 \
    libcgif0 \
    libde265-0 \
    libexif12 \
    libexpat1 \
    libffi8 \
    libfftw3-double3 \
    libfftw3-single3 \
    libgif7 \
    libgirepository-2.0-0 \
    libglib2.0-0t64 \
    libheif1 \
    libheif-plugin-aomenc \
    libheif-plugin-x265 \
    libimagequant0 \
    libjemalloc2 \
    libjpeg-turbo8 \
    libjxl0.7 \
    liblcms2-2 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libpangoft2-1.0-0 \
    libpangoxft-1.0-0 \
    libpng16-16t64 \
    libwebp7 \
    libwebpdecoder3 \
    libwebpdemux2 \
    libwebpmux3 \
    libx265-199 \
    tini \
    wget \
    && rm -rf /var/lib/apt/lists/*

ARG TARGETARCH

# Tell JVM how to find GLib
RUN if [ "$TARGETARCH" = "arm64" ]; then GNU_ARCH="aarch64-linux-gnu"; else GNU_ARCH="x86_64-linux-gnu"; fi && \
    cd /usr/lib/$GNU_ARCH && \
    ln -s libglib-2.0.so.0 libglib-2.0.so && \
    ln -s libgobject-2.0.so.0 libgobject-2.0.so && \
    ln -s libgmodule-2.0.so.0 libgmodule-2.0.so

# Install GraalVM 25 manually
ARG GRAAL_VERSION=25.0.0
ARG GRAAL_URL=https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAAL_VERSION}/graalvm-community-jdk-${GRAAL_VERSION}_linux-x64_bin.tar.gz

# Remove src.zip, man pages, and jmods to save ~100MB
RUN if [ "$TARGETARCH" = "arm64" ]; then GRAAL_ARCH="aarch64"; else GRAAL_ARCH="x64"; fi && \
    GRAAL_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAAL_VERSION}/graalvm-community-jdk-${GRAAL_VERSION}_linux-${GRAAL_ARCH}_bin.tar.gz" && \
    wget -q $GRAAL_URL -O graal.tar.gz \
    && mkdir -p /opt/java/graalvm \
    && tar -xzf graal.tar.gz -C /opt/java/graalvm --strip-components=1 \
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
COPY service/build/libs/*.jar konifer.jar
RUN chown -R konifer:konifer /app

USER konifer

## Necessary for jemalloc
ENV LD_PRELOAD="libjemalloc.so.2"

ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java --enable-native-access=ALL-UNNAMED -XX:+UseCompactObjectHeaders -Djava.io.tmpdir=/app/tmp $JAVA_OPTS -jar konifer.jar -config=application.conf -config=/app/config/konifer.conf"]
