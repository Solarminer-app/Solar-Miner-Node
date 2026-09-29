FROM eclipse-temurin:21-jdk
WORKDIR /app
EXPOSE 8080
COPY build/libs/currency-rates-1.0.0.jar currency-rates.jar
ENTRYPOINT ["java","-jar","currency-rates.jar"]
