FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl netcat-openbsd && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/distlab3-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
