FROM eclipse-temurin:21-jdk
WORKDIR /app
EXPOSE 8080
COPY build/libs/currency-rates-0.0.1-SNAPSHOT.jar currency-rates.jar
ENTRYPOINT ["java","-jar","currency-rates.jar"]