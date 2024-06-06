FROM openjdk:21-bookworm

RUN mkdir -p /app
WORKDIR /app
COPY ./target/*.jar /app/app.jar

ENV spring_profiles_active=docker

ENTRYPOINT ["java","-jar", "app.jar"]
