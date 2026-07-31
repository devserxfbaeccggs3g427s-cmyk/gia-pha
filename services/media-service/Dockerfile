FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --chown=65532:65532 target/media-service.jar /app/app.jar
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
