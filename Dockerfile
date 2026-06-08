# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests=true

# --- Stage 2: Extract Spring Boot layers ---
FROM eclipse-temurin:21-jre AS extractor
WORKDIR /app
COPY --from=builder /app/target/openinghours-*.jar app.jar
RUN java -Djarmode=tools extract --layers --launcher

# --- Stage 3: Runtime ---
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
