FROM bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:17-jre-ubi9-minimal

EXPOSE 8080

COPY scripts/entrypoint.sh /app/

ARG JAR_FILE=target/*.jar
ADD ${JAR_FILE} app.jar

RUN set -uxe && \
    chmod g=u /app/entrypoint.sh &&\
    chmod +x /app/entrypoint.s
# ENV spring_profiles_active=docker

ENTRYPOINT ["/app/entrypoint.sh","-jar","/app.jar"]