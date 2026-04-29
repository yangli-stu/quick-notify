# Quick-Notify 多阶段构建 Dockerfile
# 第一阶段：构建 JAR
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml .
COPY quick-notify-spring-boot-starter/pom.xml quick-notify-spring-boot-starter/
COPY quick-notify-example/pom.xml quick-notify-example/
RUN mvn dependency:go-offline -pl quick-notify-spring-boot-starter,quick-notify-example

COPY quick-notify-spring-boot-starter/src quick-notify-spring-boot-starter/src
COPY quick-notify-example/src quick-notify-example/src
RUN mvn package -DskipTests -pl quick-notify-example -am

# 第二阶段：运行
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache redis

COPY --from=builder /build/quick-notify-example/target/*.jar /app/app.jar
COPY *.html *.css js blog-articles /app/BOOT-INF/classes/static/
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 2025

ENTRYPOINT ["/start.sh"]
