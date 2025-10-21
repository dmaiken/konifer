FROM gradle:9.0.0-jdk24 AS cache
WORKDIR /home/gradle/app

# Copy Gradle config
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
  && rm -rf /var/lib/apt/lists/* /usr/local/src/vips-${VIPS_VERSION} \
  && rm -rf /var/lib/apt/lists/* \
  /usr/local/src/vips-${VIPS_VERSION} \
  /usr/local/src/vips-${VIPS_VERSION}.tar.xz

# Verify supported image formats
RUN vips list format

WORKDIR /app

# Copy the built jar from the service module
COPY --from=build /home/gradle/app/service/build/libs/*.jar direkt.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","direkt.jar", "-config=application.conf", "-config=/app/config/direkt.conf"]