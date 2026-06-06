FROM openjdk:21-jdk-slim

WORKDIR /app

COPY build/libs/spruce-gateway-1.1.0.jar app.jar

EXPOSE 6565

ENTRYPOINT ["java", "-jar", "app.jar"]
