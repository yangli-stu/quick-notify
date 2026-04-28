# Quick-Notify 单体应用 Dockerfile
# 后端 + 静态资源 + Redis 内置

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache redis

COPY quick-notify-example/target/*.jar /app/app.jar
COPY website/ /app/BOOT-INF/classes/static/
COPY website/start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 2025

ENTRYPOINT ["/start.sh"]
