# syntax=docker/dockerfile:1

# Image of the SumbookLM application. One process serves the REST API and the single page
# application from the same port, so the image holds one artifact and no web server of its own.

# ---------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------
# Noble rather than Alpine, on both stages. The Maven build downloads a Node toolchain and the
# embedding model ships native libraries, and both are linked against glibc.
FROM maven:3.9-eclipse-temurin-25-noble AS build

WORKDIR /build

COPY . .

# The two caches are what keeps a rebuild from fetching the world again: Maven resolves into
# /root/.m2 and the npm run of the frontend module into /root/.npm. Tests are not compiled here,
# because an image build is not the place a regression is meant to be found.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.npm \
    mvn -B -ntp -Dmaven.test.skip=true clean package

# The packaged artifact is a single file of roughly 320 MB, most of it the embedding model and its
# runtime. Taken apart into the layers the build recorded, everything that changes between two
# builds of the same dependency set lands in one layer of about a megabyte.
RUN java -Djarmode=tools -jar sumbooklm-app/target/sumbooklm.jar \
        extract --layers --launcher --destination /build/layers

# ---------------------------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runtime

# curl is what the health check of the container is run through, and is installed rather than
# assumed, so that a later base image cannot drop it without the build saying so.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --home-dir /app --uid 10001 sumbooklm

WORKDIR /app
USER sumbooklm

# Ordered least to most likely to change, so that a source change rewrites the last layer alone.
COPY --from=build --chown=sumbooklm:sumbooklm /build/layers/dependencies/ ./
COPY --from=build --chown=sumbooklm:sumbooklm /build/layers/spring-boot-loader/ ./
COPY --from=build --chown=sumbooklm:sumbooklm /build/layers/snapshot-dependencies/ ./
COPY --from=build --chown=sumbooklm:sumbooklm /build/layers/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Nothing below /api is asked for, because that prefix is refused over a plain connection whenever
# the deployment requires HTTPS, and the connection from inside the container is always plain.
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/ || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
