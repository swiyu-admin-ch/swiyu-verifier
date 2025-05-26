# SPDX-FileCopyrightText: 2025 Swiss Confederation
#
# SPDX-License-Identifier: MIT

# This code will be replaced by the content of the local.Dockerfile by the open source helper 

FROM bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:21-jre-ubi9-minimal
USER 0

EXPOSE 8080

COPY scripts/entrypoint.sh /app/

ARG JAR_FILE=verfication-application/target/*.jar
ADD ${JAR_FILE} /app/app.jar

RUN set -uxe && \
    chmod g=u /app/entrypoint.sh &&\
    chmod +x /app/entrypoint.sh

WORKDIR /app

USER 1001

ENTRYPOINT ["/app/entrypoint.sh","app.jar"]
