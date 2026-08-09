# syntax=docker/dockerfile:1

# ---- build: compile and package the CLI as a single self-contained jar ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl cli -am package \
    -DskipTests -Dspotless.check.skip=true -Dspotbugs.skip=true -Denforcer.skip=true

# ---- jlink: trim a custom JRE containing only the modules the jar actually needs ----
FROM eclipse-temurin:21-jdk AS jlink
COPY --from=build /workspace/cli/target/debt-hunter.jar /tmp/debt-hunter.jar
RUN modules="$(jdeps --multi-release 21 --print-module-deps --ignore-missing-deps /tmp/debt-hunter.jar)" && \
    jlink \
      --add-modules "${modules},jdk.crypto.ec,java.naming" \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /opt/jre-minimal

# ---- runtime: minimal, non-root image with just the trimmed JRE and the jar ----
FROM debian:12-slim AS runtime

LABEL org.opencontainers.image.title="Debt Hunter" \
      org.opencontainers.image.description="A deterministic, containerised command-line technical-debt analyser." \
      org.opencontainers.image.vendor="Debt Hunter"
ARG DEBT_HUNTER_VERSION=0.1.0-SNAPSHOT
LABEL org.opencontainers.image.version="${DEBT_HUNTER_VERSION}"

# git itself needs no network at runtime; installing it here keeps RenameTracker's rename-history
# resolution (git log --follow) fully functional inside the container, not just on dev hosts.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 debt-hunter \
    && useradd --uid 10001 --gid debt-hunter --shell /usr/sbin/nologin --no-create-home debt-hunter

COPY --from=jlink /opt/jre-minimal /opt/debt-hunter/jre
COPY --from=build /workspace/cli/target/debt-hunter.jar /opt/debt-hunter/debt-hunter.jar
COPY docker/debt-hunter /usr/local/bin/debt-hunter

RUN chmod +x /usr/local/bin/debt-hunter \
    && mkdir -p /workspace /output \
    && chown -R debt-hunter:debt-hunter /opt/debt-hunter /workspace /output

USER debt-hunter
WORKDIR /workspace
ENTRYPOINT ["debt-hunter"]
CMD ["--help"]
