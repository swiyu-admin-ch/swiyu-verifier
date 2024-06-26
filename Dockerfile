FROM bit-base-images-docker-hosted.nexus.bit.admin.ch/bit/eclipse-temurin:17-jre-ubi9-minimal

EXPOSE 8002

ARG JAR_FILE=target/*.jar
ADD ${JAR_FILE} app.jar

ENV spring_profiles_active=docker

ENTRYPOINT ["java","-jar","/app.jar"]