FROM eclipse-temurin:21-jdk
WORKDIR /app
EXPOSE 8080
COPY build/libs/solar-miner-1.0.9.jar pv-miner.jar
ENTRYPOINT ["java","-jar","pv-miner.jar"]
