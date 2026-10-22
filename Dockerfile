FROM eclipse-temurin:21-jdk-jammy@sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0 AS build

WORKDIR /workspace
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src/main ./src/main

# Integration tests run separately with Docker/Testcontainers; no Docker socket is needed during image build.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew shadowJar --no-daemon --console=plain --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process

FROM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0 AS runtime

RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/build/libs/kotlin-2026-all.jar ./app.jar
USER 10001:10001

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 \
    CMD curl --fail --silent --show-error --max-time 3 http://127.0.0.1:8080/ || exit 1

# Tomcat needs a writable base directory even when the application image is read-only.
ENTRYPOINT ["java", "-Dcatalina.base=/tmp/tomcat", "-jar", "/app/app.jar"]
CMD ["-host=0.0.0.0", "-port=8080"]
