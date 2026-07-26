FROM eclipse-temurin:25-jre
WORKDIR /app
ARG JAR=target/gateway-service-0.1.0-SNAPSHOT.jar
COPY ${JAR} app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
  CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
