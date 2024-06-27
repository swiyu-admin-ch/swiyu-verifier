FROM bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:17-jre-ubi9-minimal

RUN mkdir -p /app
WORKDIR /app
COPY ./target/*.jar /app/app.jar

ENV spring_profiles_active=docker

ENTRYPOINT ["java","-jar", "app.jar"]
