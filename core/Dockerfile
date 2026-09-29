FROM eclipse-temurin:21-jdk
WORKDIR /app
EXPOSE 8080
COPY build/libs/solar-miner-core-1.0.0.jar core.jar
ENTRYPOINT ["java","-jar","core.jar"]
