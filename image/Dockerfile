FROM openjdk:11
# FROM quarkus:2.1.0-SNAPSHOT
LABEL Author="Salduba Technologies"

WORKDIR /app

COPY target/scala-3.3.1/image-assembly-*.jar fat.jar
ARG DB_HOST_ARG=postgres
ARG DB_PORT_ARG=5432

ENV DB_HOST=${DB_HOST_ARG}
ENV DB_PORT=${DB_PORT_ARG}

EXPOSE 80

ENTRYPOINT ["java", "-jar", "/app/fat.jar"]

# CMD java -jar /app/my-app.jar
