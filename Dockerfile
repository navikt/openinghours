# --- Stage 1: Extract Spring Boot layers ---
FROM eclipse-temurin:21-jre AS extractor
WORKDIR /app
COPY target/openinghours-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination . --force

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
EXPOSE 8081

COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "org.springframework.boot.loader.launch.JarLauncher"]
