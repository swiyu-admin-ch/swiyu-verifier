

ARG SOURCE_IMAGE=bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:21-jre-ubi9-minimal
FROM ${SOURCE_IMAGE}

USER 0

EXPOSE 8080

COPY scripts/entrypoint.sh /app/

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
