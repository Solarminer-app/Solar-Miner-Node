FROM eclipse-temurin:21-jdk
WORKDIR /app
EXPOSE 8080
COPY build/libs/core-0.0.1-SNAPSHOT.jar core.jar
ENTRYPOINT ["java","-jar","core.jar"]