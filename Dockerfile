FROM eclipse-temurin:21-jdk
WORKDIR /app
EXPOSE 8080
COPY build/libs/pv-miner-0.0.1-SNAPSHOT.jar pv-miner.jar
ENTRYPOINT ["java","-jar","pv-miner.jar"]