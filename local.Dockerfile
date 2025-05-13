# SPDX-FileCopyrightText: 2025 Swiss Confederation
#
# SPDX-License-Identifier: MIT

# Dockerfile used for github
FROM eclipse-temurin:21

RUN mkdir -p /app
WORKDIR /app

COPY scripts/entrypoint.sh /app/entrypoint.sh

RUN chmod 766 $JAVA_HOME/lib/security/cacerts

ARG JAR_FILE=target/*.jar
ADD ${JAR_FILE} /app/app.jar

RUN set -uxe && \
    chmod g=u /app/entrypoint.sh &&\
    chmod +x /app/entrypoint.sh

WORKDIR /app

USER 1001

ENTRYPOINT ["/app/entrypoint.sh","app.jar"]
