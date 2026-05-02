ARG SOURCE_IMAGE=bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:21-jre-ubi9-minimal
FROM ${SOURCE_IMAGE}

# Root user is required to add CA certs to the system truststore and Java cacerts, and to set permissions on the entrypoint script
USER 0

EXPOSE 8080

COPY scripts/entrypoint.sh /app/

# Add CA cert(s) into /cacerts so the entrypoint will import them into Java cacerts at startup
COPY certs/root_ca_vi.crt /certs-app/root_ca_vi.crt

# Import the CA cert into Java cacerts so that it is available at build time
RUN keytool -importcert \
    -alias root_ca_vi \
    -file /certs-app/root_ca_vi.crt \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit \
    -noprompt

# Check if the cert was successfully added
RUN keytool -list \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit \
    | grep root_ca_vi || true

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

# Switch to non-root user with UID 1001 (non-root user in Eclipse Temurin images)
USER 1001

ENTRYPOINT ["/app/entrypoint.sh","app.jar"]