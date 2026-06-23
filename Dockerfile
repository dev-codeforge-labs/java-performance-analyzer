FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/performance-analyzer-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
