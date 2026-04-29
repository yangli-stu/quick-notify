#!/bin/sh
# 启动 Redis
redis-server --daemonize yes

# 等待 Redis 启动
sleep 2

# 启动 Java 应用
java -jar /app/app.jar
