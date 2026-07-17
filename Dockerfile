ARG BASE_IMAGE=konifer-base:latest
ARG UBUNTU_VERSION=24.04

# ==========================================
# Prune Native Libraries For The Target Architecture
# ==========================================
FROM ubuntu:${UBUNTU_VERSION} AS jar-pruner

ARG DEBIAN_FRONTEND=noninteractive
ARG TARGETARCH

RUN apt-get update && apt-get install -y --no-install-recommends zip \
    && rm -rf /var/lib/apt/lists/*

COPY service/build/libs/service-all.jar /tmp/konifer.jar

RUN zip -d /tmp/konifer.jar \
        'ai/onnxruntime/native/osx-*/*' \
        'ai/onnxruntime/native/win-*/*' \
        'native/lib/osx-*/*' \
        'native/lib/win-*/*' \
        'osx/*' \
        'windows/*' \
    && if [ "$TARGETARCH" = "amd64" ]; then \
        zip -d /tmp/konifer.jar \
            'ai/onnxruntime/native/linux-aarch64/*' \
            'native/lib/linux-aarch64/*' \
            'linux/armv6/*' \
            'linux/armv7/*' \
            'linux/armv8/*' \
            'linux/x86_32/*' \
            'linux/x86_64/musl/*'; \
    elif [ "$TARGETARCH" = "arm64" ]; then \
        zip -d /tmp/konifer.jar \
            'ai/onnxruntime/native/linux-x64/*' \
            'native/lib/linux-x86_64/*' \
            'linux/armv6/*' \
            'linux/armv7/*' \
            'linux/armv8/musl/*' \
            'linux/x86_32/*' \
            'linux/x86_64/*'; \
    else \
        echo "Unsupported target architecture: $TARGETARCH" >&2; \
        exit 1; \
    fi

# ==========================================
# The Final Runtime
# ==========================================
FROM ${BASE_IMAGE} AS runtime

RUN groupadd -r konifer && useradd -r -g konifer konifer

WORKDIR /app
RUN install -d -o konifer -g konifer /app/config /app/tmp /app/logs /app/models
COPY --from=jar-pruner --chown=konifer:konifer /tmp/konifer.jar konifer.jar

USER konifer

ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED -XX:+UseCompactObjectHeaders -Djava.io.tmpdir=/app/tmp"
EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar konifer.jar -config=application.conf -config=/app/config/konifer.conf"]
