# SPDX-FileCopyrightText: 2025 Swiss Confederation
#
# SPDX-License-Identifier: MIT

FROM eclipse-temurin:21
USER 0

EXPOSE 8080

COPY scripts/entrypoint.sh /app/

ARG JAR_FILE=verfication-application/target/*.jar
COPY ${JAR_FILE} /app/app.jar

RUN set -uxe && \
    chmod g=u /app/entrypoint.sh &&\
    chmod +x /app/entrypoint.sh

WORKDIR /app

USER 1001

ENTRYPOINT ["/app/entrypoint.sh","app.jar"]
