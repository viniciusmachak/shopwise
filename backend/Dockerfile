# syntax=docker/dockerfile:1
# Backend image. Multi-arch base images, so it builds for the ARM Oracle VM too.

# The build stage runs on the builder's own platform: the jar works on any CPU, so
# multi-arch builds only emulate the small runtime stage.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B package -DskipTests \
    && java -Djarmode=tools -jar target/shopwise-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app
# Layers ordered from least to most often changed, for smaller pulls on deploy.
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
# Non-root, by numeric id: no RUN steps in this stage, so ARM images build without emulation.
USER 1001:1001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
