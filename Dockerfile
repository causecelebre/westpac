# syntax=docker/dockerfile:1

# Build stage: compiles the jar and splits it into layers.
FROM eclipse-temurin:25.0.5.1_2-jdk-resolute@sha256:cc29fcc9d85b743118f2dd9fa5cffd7233809580c8aa4d379f021e0cac5bf524 AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY config config
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar
RUN java -Djarmode=tools -jar build/libs/westpac.jar extract --layers --launcher --destination extracted

# Runtime stage: the layers, the layer that changes most last.
FROM eclipse-temurin:25.0.5.1_2-jre-resolute@sha256:a0862ac8790c648f6b5db8a113b9990d24fbe8e65f3c4ba8d68b0302d270c1e6
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djdk.serialFilter=!*"
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# TODO(#6): add a JDK AOT cache to the image to shorten startup.
