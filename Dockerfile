FROM openjdk:21-bookworm

RUN mkdir -p /app
WORKDIR /app
COPY ./target/oid4vp-*-SNAPSHOT.jar /app/oid4vp.jar

ENV spring_profiles_active=docker

ENTRYPOINT ["java","-jar", "oid4vp.jar"]