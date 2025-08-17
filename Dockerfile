# Stage 1: Cache Gradle dependencies
FROM gradle:latest AS cache
WORKDIR /home/gradle/app

# Copy Gradle wrapper + root config
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle gradle
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY gradle.properties gradle.properties
COPY .gradle .gradle

# Copy only the service module's build scripts for dependency resolution
COPY service/build.gradle.kts service/

# Warm up the Gradle dependency cache
RUN ./gradlew :service:dependencies --no-daemon || true


# Stage 2: Build Application
FROM gradle:latest AS build
WORKDIR /home/gradle/app

# Copy everything (now including source)
COPY . .

COPY --from=cache /home/gradle/app/.gradle /home/gradle/app/.gradle
COPY --from=cache /home/gradle/app/gradlew /home/gradle/app/gradlew
COPY --from=cache /home/gradle/app/gradlew.bat /home/gradle/app/gradlew.bat
COPY --from=cache /home/gradle/app/gradle /home/gradle/app/gradle

# Build the service fat jar only
RUN ./gradlew :service:buildFatJar --no-daemon


# Stage 3: Create the Runtime Image
FROM eclipse-temurin:24-jre AS runtime

RUN apt-get -y update && apt-get -y install \
  # VIPS build dependencies
  build-essential \
  pkg-config \
  ninja-build \
  wget \
  meson \
  # VIPS dynamic modules for extra functionality
  glib2.0-dev \
  libarchive-dev \
  # EXIF
  libexif-dev \
  libexpat1 \
  libfftw3-dev \
  # Image quantization
  libimagequant-dev \
  # JPEG
  libjpeg62 \
  # Little CMS
  liblcms2-dev \
  liborc-0.4-dev \
  # Text rendering
  libpango1.0-dev \
  # PNG
  libpng-dev \
  # PDF
  libpoppler-glib-dev \
  # SVG
  librsvg2-dev \
  librsvg2-2 \
  # TIFF
  libtiff5-dev \
  # WebP
  libwebp-dev \
  # HEIC/HEIF
  libheif-dev \
  libde265-dev \
  x265 \
  # Raw format
  libraw-dev \
  # JPEG XL
  libjxl-dev \
  # GIF
  libgif-dev

ENV VIPS_VERSION=8.17.1
ARG VIPS_URL=https://github.com/libvips/libvips/releases/download

WORKDIR /usr/local/src

ENV LD_LIBRARY_PATH=/lib:/usr/lib:/usr/local/lib

RUN wget ${VIPS_URL}/v${VIPS_VERSION}/vips-${VIPS_VERSION}.tar.xz

RUN tar xf vips-${VIPS_VERSION}.tar.xz \
  && cd vips-${VIPS_VERSION} \
  && meson build --buildtype=release --libdir=lib \
  && cd build \
  && ninja \
  && ninja install \
  # Clean up build tools and meson
  && apt-get remove -y build-essential pkg-config ninja-build \
  && apt-get autoremove -y \
  && rm -rf /var/lib/apt/lists/* /usr/local/src/vips-${VIPS_VERSION}

# Verify supported image formats
RUN vips list format

WORKDIR /app

# Copy the built jar from the service module
COPY --from=build /home/gradle/app/service/build/libs/*.jar tessa.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","tessa.jar", "-config=application.conf", "-config=/app/config/tessa.conf"]