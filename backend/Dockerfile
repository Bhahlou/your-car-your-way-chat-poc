# Stage 1: build (JDK 25 + Maven)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# Dependencies first: this layer stays cached as long as pom.xml does not change
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Stage 2: minimal runtime image (JRE only, unprivileged user)
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /build/target/chat-poc-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
