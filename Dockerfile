# ==========================================
# The Final Runtime
# ==========================================
ARG BASE_IMAGE=konifer-base:latest
FROM ${BASE_IMAGE} AS runtime

RUN groupadd -r konifer && useradd -r -g konifer konifer

WORKDIR /app
RUN mkdir -p /app/config /app/tmp /app/logs
COPY service/build/libs/*.jar konifer.jar
RUN chown -R konifer:konifer /app

USER konifer

ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED -XX:+UseCompactObjectHeaders -Djava.io.tmpdir=/app/tmp"
EXPOSE 8080

ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar konifer.jar -config=application.conf -config=/app/config/konifer.conf"]
