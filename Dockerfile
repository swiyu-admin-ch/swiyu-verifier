FROM eclipse-temurin:21

EXPOSE 8002

ARG JAR_FILE=target/*.jar
ADD ${JAR_FILE} app.jar

ENV spring_profiles_active=docker

ENTRYPOINT ["java","-jar","/app.jar"]