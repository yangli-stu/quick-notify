# Spring Boot 3 + WebSocket STOMP + 集群会话 + Token 认证集成示例

(该代码已用于生产验证，轻松应对万级日活用户)

## 🔧 Demo 演示

* 本地启动：安装好redis后直接启动即可，无需依赖mysql
```bash
docker run -d --name redis -p 6379:6379 redis
```
* 打开浏览器访问：`src/main/resources/stomp-websocket-sockjs.html`
* 示例截图：

  ![img.png](img.png)

---

## 📖 项目简介

本项目展示了如何在 Spring Boot 应用中集成 WebSocket + STOMP，实现：

* 实时消息通信
* Token 用户认证
* 点对点消息推送
* **消息确认机制（ACK）**：确保消息可靠送达，支持自动重试
* 集群环境下的 WebSocket 会话转发

适用于服务器消息推送，社交类消息通知、实时状态更新、在线客服等场景。

---

## 🧱 核心模块结构

路径：`src/main/java/io/stu/notify/stomp`

```
├── NotifyMessage.java               // WebSocket 消息结构定义
├── NotifyType.java                  // 消息类型枚举
├── StompWebSocketHandler.java       // 消息推送管理器
├── StompWebsocketConfig.java        // STOMP/WebSocket 配置
└── StompWebsocketInterceptor.java   // 鉴权拦截器
```

### 🔌 `StompWebsocketConfig`

* **作用**：配置 WebSocket 端点与消息代理。
* **说明**：实现 `WebSocketMessageBrokerConfigurer` 接口。

### 🛡️ `StompWebsocketInterceptor`

* **作用**：拦截 WebSocket 连接请求，基于 Token 鉴权。
* **说明**：实现 `ChannelInterceptor`，绑定用户与会话。

### 📬 `StompWebSocketHandler`

* **作用**：管理用户会话及消息发送（广播/点对点），实现消息确认（ACK）机制。
* **说明**：封装 `SimpMessagingTemplate`，提供统一的推送入口，支持消息可靠送达与自动重试。

### 💬 `NotifyMessage`

* **作用**：自定义的消息格式对象。
* **字段**：消息 ID、接收者、消息体、类型、状态等。

### 🧾 `NotifyType`

* **作用**：定义支持的通知类型。
* **说明**：使用枚举 + 类型校验机制，避免数据结构不一致。

---

## 🚀 使用示例

### 广播消息

```java
NotifyMessage message = NotifyMessage.builder()
    .id("msg-123")
    .data("广播消息内容")
    .type(NotifyType.STRING_MSG.name())
    .build();

webSocketHandler.broadcastMessage(message);
```

### 向指定用户发送消息

```java
NotifyMessage message = NotifyMessage.builder()
    .id("msg-123")
    .receiver("userId")
    .data("消息内容")
    .type(NotifyType.STRING_MSG.name())
    .build();

webSocketHandler.sendMessage(message, null);
```

### 向指定用户发送消息（带 ACK 确认）

```java
NotifyMessage message = NotifyMessage.builder()
    .id("msg-123")
    .receiver("userId")
    .data("消息内容")
    .type(NotifyType.STRING_MSG.name())
    .build();

webSocketHandler.sendMessageWithAck(message);
```

**说明**：使用 `sendMessageWithAck` 发送的消息会：
1. 为每个用户会话创建 ACK 记录
2. 等待客户端确认（默认 5 秒）
3. 未确认时自动重试（最多 12 次，总时长 1 分钟）

---

## ✅ 消息确认机制（ACK）

### 🎯 设计目标

确保消息可靠送达，解决网络抖动、客户端离线等场景下的消息丢失问题。

### 📋 核心特性

* **自动重试**：未收到 ACK 时自动重试发送
* **会话级追踪**：为每个 WebSocket 会话独立追踪消息状态
* **过期清理**：超时消息自动清理，避免内存泄漏
* **集群支持**：基于 Redis 实现分布式 ACK 管理

### 🔄 工作流程

```
1. 发送消息
   └─> 为每个 session 创建 ACK 记录（存储到 Redis）
   
2. 客户端接收消息
   └─> 通过 /app/ack 端点发送 ACK 确认
   
3. 服务端处理 ACK
   └─> 从 Redis 移除对应记录
   
4. 定时任务（每 5 秒）
   ├─> 检查未确认消息（创建时间 > 5 秒）
   ├─> 重试发送（未超过最大重试次数）
   └─> 清理过期消息（超过 TTL 或重试次数）
```

### ⚙️ 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `ACK_CHECK_WAIT_MS` | 5 秒 | 等待 ACK 的时间窗口 |
| `ACK_RETRY_INTERVAL_MS` | 5 秒 | 重试间隔 |
| `ACK_MESSAGE_TTL_MS` | 60 秒 | 消息最大存活时间 |
| `ACK_MAX_RETRY_COUNT` | 12 次 | 最大重试次数（自动计算） |

**位置**：`StompWebSocketHandler.java` 第 38-41 行

### 📦 存储方式

#### Redis 模式（默认）

* **存储位置**：Redis Map (`stomp::pending_messages`)
* **Key 格式**：`messageId::sessionId`
* **优势**：支持集群部署，数据持久化
* **适用场景**：生产环境、多节点部署

#### 本地缓存模式（可选）

* **存储位置**：内存 `ConcurrentHashMap`
* **配置**：设置 `enableLocalAck = true`
* **优势**：性能更高，无网络开销
* **适用场景**：单节点部署、开发测试

### 🔌 客户端集成

#### 1. 接收消息并发送 ACK

```javascript
stompClient.subscribe('/user/queue/msg', function (message) {
    const msgData = JSON.parse(message.body);
    console.log('收到消息:', msgData);
    
    // 发送 ACK 确认
    stompClient.send('/app/ack', {}, msgData.id);
});
```

#### 2. STOMP 端点

* **接收消息**：`/user/queue/msg`
* **发送 ACK**：`/app/ack`（Payload 为消息 ID）

### 📊 监控与日志

系统会记录以下日志：

* `[ACK-REDIS] 消息入队`：消息进入 ACK 队列
* `[ACK-REDIS] 确认成功`：收到客户端 ACK
* `[ACK-REDIS] 重发`：触发重试机制
* `[ACK-REDIS] 消息过期/超限`：消息被清理

**示例日志**：
```
[ACK-REDIS] 消息入队, msgId msg-123, sessionId sess-456, receiver user-789
[ACK-REDIS] 确认成功, msgId msg-123, sessionId sess-456, retryCount 0
[ACK-REDIS] 定时处理完成, total 10, retried 2, expired 1, not online 0
```

### 🎨 架构设计

```
┌─────────────────┐
│  sendMessageWithAck │
└────────┬──────────┘
         │
         ├─> 发送消息到 WebSocket
         │
         └─> 创建 ACK 记录（Redis）
                    │
                    ▼
         ┌──────────────────┐
         │  Redis Map      │
         │  (pending_msgs) │
         └────────┬─────────┘
                  │
         ┌────────┴─────────┐
         │                   │
    ┌────▼────┐        ┌─────▼─────┐
    │ 客户端ACK │        │  定时重试任务 │
    │ /app/ack │        │ (每5秒)    │
    └────┬────┘        └─────┬─────┘
         │                   │
         └─────────┬─────────┘
                   │
            ┌──────▼──────┐
            │ 移除ACK记录  │
            └─────────────┘
```

### 💡 最佳实践

1. **消息 ID 唯一性**：确保消息 ID 全局唯一，避免 ACK 冲突
2. **及时发送 ACK**：客户端收到消息后立即发送 ACK，避免不必要的重试
3. **处理重复消息**：由于重试机制，客户端可能收到重复消息，需要做幂等处理
4. **监控重试率**：关注日志中的重试次数，评估网络质量

### ⚠️ 注意事项

* **消息去重**：客户端应实现消息去重逻辑（基于消息 ID）
* **离线处理**：用户离线时，消息会在 TTL 到期后自动清理
* **多会话场景**：同一用户多个会话时，每个会话独立追踪 ACK
* **性能影响**：大量未确认消息会增加 Redis 内存占用，建议监控

---

# ☁️ 集群模式：跨节点会话转发

## ✨ 核心类：`StompNotifyEventListener`

用于支持 **分布式 WebSocket 会话处理**，结合 Redisson + Redis Topic 实现。

### 📌 处理流程

1. **检查本地会话**

    * 如果当前节点存在目标用户会话：**直接推送**
    * 否则：**广播事件到其他节点**

2. **集群事件广播**

    * 使用 `Redisson` 发布事件到 Redis Topic
    * 所有节点订阅该 Topic 实现集群通信

3. **跨节点接收处理**

    * 接收到广播事件后判断是否拥有目标会话
    * 若存在：推送给用户，否则忽略

---

## 📈 架构图（简化版）

```
  NotifyMessageEvent
         │
         ▼
 StompNotifyEventListener
         │
    ┌────┴────────────┐
    │                 │
[当前节点有会话]   [无会话：广播事件]
    │                 │
    ▼                 ▼
WebSocket推送     Redis发布事件
                      │
                      ▼
           其他节点监听事件并推送
```

---

## 💾 消息持久化与业务集成

### 核心类：`NotifyManager`

负责将消息保存至数据库，并异步推送至集群节点。

```java
@Transactional(rollbackFor = Throwable.class)
public NotifyMessageLog saveAndPublish(NotifyMessageLog msg) {
    NotifyType.valueOf(msg.getType()).checkDataType(msg.getData());

    // 1. 消息持久化
    notifyMessageLogRepository.save(msg);

    // 2. 异步推送事件
    SpringContextUtil.publishEvent(new NotifyMessageEvent(msg));
    return msg;
}
```

---

## ✅ 总结

本项目提供了一个完整的 WebSocket 实时通信集成方案，覆盖：

* ✅ STOMP 协议支持
* ✅ Token 认证与用户绑定
* ✅ **消息确认机制（ACK）**：可靠送达、自动重试
* ✅ 分布式消息转发（基于 Redis）
* ✅ 消息持久化与业务解耦

可作为企业级项目中的即时通讯/通知模块的参考模板。

---

## 📚 快速开始

### 1. 环境准备

```bash
# 启动 Redis
docker run -d --name redis -p 6379:6379 redis
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

### 3. 测试 ACK 功能

1. 打开浏览器访问：`src/main/resources/stomp-websocket-sockjs.html`
2. 使用 Token 连接（如：`test1`）
3. 通过 API 发送消息：
   ```bash
   curl -X POST -d "测试消息" http://localhost:2025/vh-stomp-wsend/push_all_obj/test1
   ```
4. 观察浏览器控制台，确认收到消息并自动发送 ACK
5. 查看服务端日志，确认 ACK 处理流程

### 4. 测试重试机制

断开网络连接后发送消息，观察服务端日志中的重试记录。
