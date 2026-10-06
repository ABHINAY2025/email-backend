# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

# --- runtime ---
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 999 applyflow
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER applyflow
# Sized for small instances (Render free = 512 MB). PORT is provided by the host; defaults to 8080.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
