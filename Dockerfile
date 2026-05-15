FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
RUN sed -i 's/\r//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon -q

COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]