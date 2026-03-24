# --- step 1：build jar ---
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# copy pom.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B

# copy source code
COPY . .
RUN mvn clean package -DskipTests=false

# --- step 2：run jar  ---
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app
EXPOSE 8081

# copy jar
COPY --from=builder /app/target/openinghours-*.jar app.jar

# start application
ENTRYPOINT ["java", "-jar", "app.jar"]