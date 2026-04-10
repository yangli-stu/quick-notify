# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Quick-Notify 是一个基于 Spring Boot 3 + WebSocket STOMP + Redis 的企业级实时消息推送系统。支持通过 Redis Pub/Sub 实现多节点水平扩展，通过 ACK 确认机制确保消息可靠送达。

## 构建命令

```bash
# 运行应用（自动激活 dev 环境）
mvn spring-boot:run

# 构建 JAR
mvn clean package -DskipTests

# 运行测试
mvn test
```

服务默认运行在 2025 端口，dev 环境自动激活。

## 架构

```
客户端 (SockJS + STOMP) → WebSocket Endpoint → StompWebsocketInterceptor → StompWebSocketHandler
                                                                                    ↓
                                                                          Redis Pub/Sub (集群)
                                                                                    ↓
                                                                          其他节点
```

### 核心组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `StompWebsocketInterceptor` | `notify/stomp/` | Token 认证、连接数限制（每人最多 10 个） |
| `StompWebSocketHandler` | `notify/stomp/` | 消息发送、ACK 追踪、心跳（10秒间隔，30秒断开） |
| `StompNotifyEventListener` | `notify/event/` | Redis Pub/Sub 监听，处理跨节点消息 |
| `NotifyManager` | `notify/` | 消息持久化和发布编排 |
| `NotifyMessageLog` | `notify/model/` | JPA 实体，存储消息（雪花算法 ID） |

### 消息流程

1. REST API 调用 `NotifyManager.saveAndPublish()`
2. 消息通过 JPA 持久化到 MySQL
3. `StompNotifyEventListener` 接收事件，发布到 Redis Topic
4. 所有集群节点通过 Pub/Sub 接收事件
5. 每个节点检查目标用户是否有本地会话 `hasSession(receiver)`
6. 如果有，`sendMessageWithAck()` 发送消息并在 Redis 中创建 ACK 记录

### ACK 机制

- 消息在 Redis Hash `stomp::pending_messages` 中追踪，key 为 `{msgId}::{sessionId}`
- 首次等待 5 秒，之后每 5 秒重试
- 最多重试 12 次或 60 秒 TTL
- 定时任务 `retryRedisMessages()` 每 5 秒处理重试
- 客户端需发送 ACK 到 `/app/ack`，带上消息 ID

### 集群模式

Redis Topic `stomp::ws_notify_topic` 处理跨节点通信：
- 本地事件发布 → 广播到所有节点
- 远程事件接收 → 检查本地会话，有则投递

## 扩展消息类型

在 `NotifyType.java` 中添加新类型：
```java
ORDER_STATUS(OrderStatusData.class);
```

`checkDataType()` 方法验证数据类型是否匹配。

## 重要配置

- 服务端口：`2025`
- 心跳：发送/接收 10 秒，30 秒无心跳断开
- ACK 重试：首次等待 5 秒，间隔 5 秒，最多 12 次，60 秒 TTL
- 每用户最大连接数：10
- 消息默认雪花 ID 前缀：`ntf_`

## Redis Key

- `stomp::pending_messages` - Hash，存储待确认消息
- `stomp::ws_notify_topic` - Pub/Sub Topic，用于集群消息通信
