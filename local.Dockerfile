# Dockerfile used for local builds and github
FROM eclipse-temurin:21

RUN mkdir -p /app
WORKDIR /app
COPY ./target/*.jar /app/app.jar

ENTRYPOINT ["java","-jar", "app.jar"]