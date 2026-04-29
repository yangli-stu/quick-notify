# Quick-Notify 多阶段构建 Dockerfile
# 构建上下文: . (项目根目录)

FROM maven:3.9-eclipse-temurin-21 AS maven-build

WORKDIR /build
COPY pom.xml .
COPY quick-notify-spring-boot-starter/pom.xml quick-notify-spring-boot-starter/
COPY quick-notify-example/pom.xml quick-notify-example/
RUN mvn dependency:go-offline -pl quick-notify-spring-boot-starter,quick-notify-example
COPY quick-notify-spring-boot-starter/src quick-notify-spring-boot-starter/src
COPY quick-notify-example/src quick-notify-example/src
RUN mvn package -DskipTests -pl quick-notify-example -am

# 最终阶段
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache redis

COPY --from=maven-build /build/quick-notify-example/target/*.jar /app/app.jar
COPY website/*.html website/*.css website/js website/blog-articles /app/BOOT-INF/classes/static/
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 2025

ENTRYPOINT ["/start.sh"]
