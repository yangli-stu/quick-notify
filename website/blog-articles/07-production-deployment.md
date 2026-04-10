---
title: 【部署】生产环境部署与运维指南
date: 2026-04-11
tags: [部署, 运维, Docker, Kubernetes, 生产环境]
description: 详细介绍 Quick-Notify 生产环境部署方案，包括 Docker Compose、Kubernetes、负载均衡配置，以及运维监控和故障排查。
---

## 前言

本文详细介绍 Quick-Notify 生产环境部署方案，涵盖从单机到集群的各种部署模式，以及日常运维和故障排查指南。

## 一、部署架构

### 1.1 生产架构总览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            生产环境架构                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────┐                                                      │
│   │   用户端    │                                                      │
│   │ 浏览器/App  │                                                      │
│   └──────┬──────┘                                                      │
│          │                                                              │
│          ▼                                                              │
│   ┌─────────────┐     ┌─────────────────────────────────────────┐     │
│   │   CDN/WAF   │────▶│              负载均衡层                  │     │
│   │ 加速/防护   │     │           (NGINX / ALB)                  │     │
│   └─────────────┘     └─────────────────┬───────────────────────┘     │
│                                         │                              │
│                                         ▼                              │
│   ┌────────────────────────────────────────────────────────────────┐  │
│   │                        应用集群 (3+ 节点)                        │  │
│   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │  │
│   │  │   节点 1     │  │   节点 2     │  │   节点 N     │           │  │
│   │  │  Spring Boot │  │  Spring Boot │  │  Spring Boot │           │  │
│   │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │  │
│   └─────────┼─────────────────┼─────────────────┼────────────────────┘  │
│             │                 │                 │                       │
│             └─────────────────┼─────────────────┘                       │
│                               ▼                                        │
│   ┌────────────────────────────────────────────────────────────────┐  │
│   │                         存储层                                  │  │
│   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │  │
│   │  │    Redis     │  │    MySQL     │  │   监控告警    │         │  │
│   │  │ Sentinel/    │  │  主从复制    │  │  Prometheus  │         │  │
│   │  │ Cluster      │  │              │  │  + Grafana   │         │  │
│   │  └──────────────┘  └──────────────┘  └──────────────┘         │  │
│   └────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 组件版本推荐

| 组件 | 推荐版本 | 说明 |
|------|---------|------|
| Java | 21 LTS | 长期支持版本 |
| Spring Boot | 3.2.x | 最新稳定版 |
| Redis | 7.2.x | 支持 Redis Stream |
| MySQL | 8.0.x | 支持 JSON 类型 |
| NGINX | 1.25.x | 支持 HTTP/2 |

## 二、Docker Compose 部署

### 2.1 docker-compose.yml

```yaml
version: '3.8'

services:
  # ========== 基础设施 ==========

  redis:
    image: redis:7-alpine
    container_name: quick-notify-redis
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
      - ./redis.conf:/usr/local/etc/redis/redis.conf
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - quick-notify

  mysql:
    image: mysql:8.0
    container_name: quick-notify-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: quicknotify
      MYSQL_USER: qnuser
      MYSQL_PASSWORD: qnpass
    volumes:
      - mysql_data:/var/lib/mysql
      - ./schema.sql:/docker-entrypoint-initdb.d/schema.sql
    ports:
      - "3306:3306"
    command: --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - quick-notify

  # ========== 应用节点 ==========

  app1:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: quick-notify-app1
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/quicknotify
      - SPRING_REDIS_HOST=redis
      - SERVER_PORT=8080
      - INSTANCE_ID=app1
    ports:
      - "8080:8080"
    depends_on:
      redis:
        condition: service_healthy
      mysql:
        condition: service_healthy
    networks:
      - quick-notify
    deploy:
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G

  app2:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: quick-notify-app2
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/quicknotify
      - SPRING_REDIS_HOST=redis
      - SERVER_PORT=8080
      - INSTANCE_ID=app2
    ports:
      - "8081:8080"
    depends_on:
      redis:
        condition: service_healthy
      mysql:
        condition: service_healthy
    networks:
      - quick-notify
    deploy:
      resources:
        limits:
          memory: 2G

  # ========== 负载均衡 ==========

  nginx:
    image: nginx:alpine
    container_name: quick-notify-nginx
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - app1
      - app2
    networks:
      - quick-notify

volumes:
  redis_data:
  mysql_data:

networks:
  quick-notify:
    driver: bridge
```

### 2.2 nginx.conf

```nginx
upstream backend {
    least_conn;

    server app1:8080 weight=1;
    server app2:8080 weight=1;

    keepalive 32;
}

server {
    listen 80;
    server_name your-domain.com;

    # WebSocket 升级
    location /stomp-ws {
        proxy_pass http://backend;

        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 超时配置
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;

        # 连接复用
        proxy_buffering off;
        proxy_cache off;
    }

    # REST API
    location /api {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 静态资源
    location / {
        root /usr/share/nginx/html;
        index index.html;
    }
}
```

### 2.3 启动脚本

```bash
#!/bin/bash
# deploy.sh

set -e

echo "========== 部署 Quick-Notify =========="

# 构建镜像
echo "构建镜像..."
docker-compose build

# 启动服务
echo "启动服务..."
docker-compose up -d

# 等待健康检查
echo "等待服务就绪..."
sleep 30

# 检查状态
echo "检查服务状态..."
docker-compose ps

echo "========== 部署完成 =========="
echo "访问地址: http://localhost"
```

## 三、Kubernetes 部署

### 3.1 Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: quick-notify
  labels:
    app: quick-notify
spec:
  replicas: 3
  selector:
    matchLabels:
      app: quick-notify
  template:
    metadata:
      labels:
        app: quick-notify
    spec:
      containers:
        - name: app
          image: quick-notify:latest
          ports:
            - containerPort: 8080
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "k8s"
            - name: SPRING_REDIS_HOST
              value: "redis-master"
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                secretKeyRef:
                  name: db-secret
                  key: url
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "2Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
```

### 3.2 Service

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: quick-notify-service
spec:
  selector:
    app: quick-notify
  ports:
    - port: 80
      targetPort: 8080
      name: http
  type: ClusterIP
```

### 3.3 Ingress

```yaml
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: quick-notify-ingress
  annotations:
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
    nginx.ingress.kubernetes.io/upstream-hash-by: "$remote_addr"
spec:
  rules:
    - host: quick-notify.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: quick-notify-service
                port:
                  number: 80
```

## 四、配置清单

### 4.1 application-prod.yml

```yaml
spring:
  application:
    name: quick-notify
  profiles:
    active: prod

  datasource:
    url: ${DB_URL:jdbc:mysql://mysql:3306/quicknotify}
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:password}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false

server:
  port: 8080
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain
  tomcat:
    threads:
      max: 200
      min-spare: 10

redisson:
  single-server-config:
    address: redis://${REDIS_HOST:localhost}:6379
    connection-pool-size: 64
    connection-minimum-idle-size: 24
    idle-connection-timeout: 10000
    connect-timeout: 10000
    timeout: 3000
    retry-attempts: 3
    retry-interval: 1500

logging:
  level:
    root: INFO
    io.stu.notify: INFO
  file:
    name: /var/log/quick-notify/app.log
```

## 五、监控告警

### 5.1 Actuator 端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### 5.2 自定义监控指标

```java
@Component
public class WebSocketMetrics {
    private final MeterRegistry meterRegistry;

    public WebSocketMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordMessageSent(String type) {
        meterRegistry.counter("websocket.messages.sent", "type", type).increment();
    }

    public void recordAckReceived() {
        meterRegistry.counter("websocket.ack.received").increment();
    }

    public void recordConnection() {
        meterRegistry.gauge("websocket.connections",
            SimpUserRegistry.getUserCount());
    }
}
```

### 5.3 告警规则

```yaml
# Prometheus 告警规则
groups:
  - name: quick-notify
    rules:
      - alert: HighPendingMessages
        expr: redis_pending_messages > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "待确认消息过多"

      - alert: ConnectionFailed
        expr: rate(websocket_connect_failed_total[5m]) > 0.1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "WebSocket 连接失败率过高"
```

## 六、日志管理

### 6.1 结构化日志

```json
{
    "timestamp": "2026-04-11T10:30:00.000Z",
    "level": "INFO",
    "logger": "StompWebSocketHandler",
    "message": "[ACK-REDIS] 消息入队",
    "context": {
        "msgId": "msg_123",
        "sessionId": "sess_456",
        "receiver": "user789"
    }
}
```

### 6.2 ELK 集成

```yaml
# filebeat.yml
filebeat.inputs:
  - type: container
    paths:
      - /var/lib/docker/containers/*/*.log
    processors:
      - add_kubernetes_metadata:
          host: ${NODE_NAME}
          matchers:
            - logs_path:
                logs_path: "/var/lib/docker/containers/"

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
```

## 七、故障排查

### 7.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 消息发送失败 | Redis 连接断开 | 检查 Redis 健康状态 |
| ACK 超时 | 网络延迟 | 调大 ACK_CHECK_WAIT_MS |
| 连接数过多 | 连接未释放 | 检查心跳配置 |
| 消息乱序 | 多节点并发 | 客户端实现排序 |

### 7.2 诊断命令

```bash
# 检查服务状态
docker-compose ps

# 查看日志
docker-compose logs -f app1

# Redis 连接测试
redis-cli -h localhost -p 6379 ping

# MySQL 连接测试
mysql -h localhost -u root -p -e "SELECT 1"

# WebSocket 连接测试
wscat -c ws://localhost/stomp-ws
```

## 八、总结

本文介绍了 Quick-Notify 生产环境部署方案：

- **Docker Compose**：快速搭建开发/测试环境
- **Kubernetes**：生产级容器编排
- **NGINX**：负载均衡和 WebSocket 代理
- **监控告警**：Prometheus + Grafana
- **日志收集**：ELK 集成

---

## 下一步

- 🔐 [Token 认证安全](./08-security-authentication.md)
- ⚡ [性能优化](./09-performance-optimization.md)
- 📝 [最佳实践汇总](./10-best-practices.md)
