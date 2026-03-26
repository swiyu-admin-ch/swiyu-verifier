ARG SOURCE_IMAGE=bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:21-jre-ubi9-minimal
FROM ${SOURCE_IMAGE}

USER 0

EXPOSE 8080

COPY scripts/entrypoint.sh /app/

# Add CA cert(s) into /certs-app so the entrypoint will import them into Java cacerts at startup
COPY certs/root_ca_vi.crt /certs-app/root_ca_vi.crt
#RUN mkdir -p /certs-app && chmod 755 /certs-app && chmod 644 /certs-app/root_ca_vi.crt || true

ARG JAR_FILE=verifier-application/target/*.jar
COPY ${JAR_FILE} /app/app.jar

RUN set -uxe && \
    chmod g=u /app/entrypoint.sh &&\
    chmod +x /app/entrypoint.sh

WORKDIR /app

# All image-specific envvars can easiliy be printed out by simply running:
#     podman inspect <IMAGE_NAME> --format='{{json .Config.Env}}' | jq -r '.[]|select(startswith("ISSUER_"))'
ENV JAVA_BOOTCLASSPATH="./lib"
VOLUME ${JAVA_BOOTCLASSPATH}

USER 1001

ENTRYPOINT ["/app/entrypoint.sh","app.jar"]