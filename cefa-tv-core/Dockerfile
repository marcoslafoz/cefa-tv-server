# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:resolve

COPY src/ src/
RUN ./mvnw -B -DskipTests clean package && \
    mv target/*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN apk add --no-cache ffmpeg fontconfig ttf-dejavu && \
    addgroup --system --gid 1001 cefatv && \
    adduser --system --uid 1001 --ingroup cefatv cefatv && \
    mkdir -p /app/tmp && \
    chown -R cefatv:cefatv /app

COPY --from=builder --chown=cefatv:cefatv /app/target/app.jar app.jar

USER cefatv

ENV MEDIA_TEMP_DIR=/app/tmp
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
