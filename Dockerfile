FROM gradle:9.0.0-jdk24 AS cache
WORKDIR /home/gradle/app
COPY gradle gradle
COPY gradle.properties .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY service/build.gradle.kts service/
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle --no-transfer-progress :service:dependencies || true

FROM gradle:9.0.0-jdk24 AS build
WORKDIR /home/gradle/app
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle gradle :service:buildFatJar

FROM eclipse-temurin:24-jre AS runtime

ENV VIPS_VERSION=8.17.1
ENV LD_LIBRARY_PATH=/lib:/usr/lib:/usr/local/lib

# Copy and Run the VIPS installer
# We copy it to a temp location, run it, then it cleans itself up
COPY scripts/install-vips.sh /usr/local/bin/install-vips.sh
RUN chmod +x /usr/local/bin/install-vips.sh \
    && /usr/local/bin/install-vips.sh --with-deps --cleanup

# Verify
RUN vips list format

WORKDIR /app
COPY --from=build /home/gradle/app/service/build/libs/*.jar direkt.jar

ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["java","-jar","direkt.jar", "-config=application.conf", "-config=/app/config/direkt.conf"]