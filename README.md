<p align="center">
  <h1 align="center">Quick-Notify</h1>
  <p align="center">
    基于 Spring Boot 3 + WebSocket STOMP + Redis 的企业级实时消息推送系统
    <br />
    <strong>已在生产环境验证，轻松应对万级日活用户</strong>
  </p>
</p>

<p align="center">
  <a href="#特性">特性</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#核心功能详解">核心功能详解</a> •
  <a href="#接入指南">接入指南</a> •
  <a href="#api-文档">API 文档</a>
</p>

---

## 特性

| 特性 | 描述 |
|------|------|
| **实时推送** | 基于 WebSocket + STOMP 协议，毫秒级消息送达 |
| **Token 认证** | 支持 JWT 等多种认证方式，安全可靠 |
| **消息确认 (ACK)** | 自动重试机制，确保消息可靠送达 |
| **集群支持** | 基于 Redis Pub/Sub，支持多节点水平扩展 |
| **消息持久化** | 支持历史消息存储与查询 |
| **开箱即用** | 完整的前后端示例，5 分钟快速上手 |

---

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.11 | 应用框架 |
| Java | 21 | 开发语言 |
| WebSocket + STOMP | - | 实时通信协议 |
| SockJS | 1.x | 浏览器兼容降级 |
| Redisson | 3.37.0 | Redis 客户端 + 分布式支持 |
| CosId | 2.8.1 | 分布式雪花 ID 生成 |
| Spring Data JPA | - | 数据持久化 |

---

## 快速开始

### 环境要求

- Java 21+
- Maven 3.8+
- Redis 6+ (或 Docker)

### 1. 启动 Redis

```bash
docker run -d --name redis -p 6379:6379 redis
```

### 2. 克隆并运行

```bash
git clone https://github.com/your-repo/quick-notify.git
cd quick-notify
mvn spring-boot:run
```

### 3. 打开测试页面

浏览器访问 `http://localhost:2025/stomp-websocket-sockjs.html`

![Demo Screenshot](img.png)

### 4. 发送测试消息(模拟服务器推送消息)

```bash
# 发送给指定用户
curl -X POST -d "Hello World" http://localhost:2025/vh-stomp-wsend/push_all_obj/test1

# 广播给所有在线用户
curl -X POST -d "广播消息" http://localhost:2025/vh-stomp-wsend/push_all_obj
```

---

## 核心功能详解

### 1. Token 认证

#### 认证流程

```
┌──────────────┐                              ┌──────────────┐
│    客户端     │                              │    服务端     │
└──────┬───────┘                              └──────┬───────┘
       │                                             │
       │  1. STOMP CONNECT                           │
       │  ┌─────────────────────────────┐            │
       │  │ CONNECT                      │           │
       │  │ Authorization: Bearer <JWT>  │           │
       │  │ accept-version: 1.2          │           │
       │  └─────────────────────────────┘            │
       │ ─────────────────────────────────────────>  │
       │                                             │
       │                    2. StompWebsocketInterceptor 拦截
       │                       ├─ 提取 Authorization 头
       │                       ├─ 解析 Token 获取 userId
       │                       ├─ 校验用户连接数 (≤10)
       │                       └─ 绑定 Principal 到会话
       │                                             │
       │  3. STOMP CONNECTED                         │
       │ <─────────────────────────────────────────  │
       │                                             │
       │  4. 后续消息自动携带用户身份                    │
       │ ─────────────────────────────────────────>  │
```

#### 核心代码

**服务端拦截器** (`StompWebsocketInterceptor.java`)：

```java
@Component
public class StompWebsocketInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message, StompHeaderAccessor.class);
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 1. 从请求头提取 Token
            String token = accessor.getNativeHeader("Authorization").get(0);
            
            // 2. 解析 Token 获取用户ID（支持 JWT 或自定义方式）
            String userId = extractUserFromToken(token);
            
            // 3. 校验用户连接数限制
            List<String> existingSessions = Optional
                .ofNullable(simpUserRegistry.getUser(userId))
                .map(SimpUser::getSessions)
                .map(sessions -> sessions.stream().map(SimpSession::getId).toList())
                .orElse(List.of());
                
            if (existingSessions.size() > 10) {
                throw new IllegalStateException("用户连接数超限");
            }
            
            // 4. 绑定用户身份到 WebSocket 会话
            accessor.setUser(new MyPrincipal(userId));
        }
        return message;
    }
    
    private String extractUserFromToken(String token) {
        // 开发环境：直接使用 token 作为用户ID
        if (SpringContextUtil.isDevEnv() && token.startsWith("test")) {
            return token;
        }
        // 生产环境：解析 JWT
        return SpringContextUtil.getBean(JwtDecoder.class)
            .decode(token).getSubject();
    }
}
```

**客户端连接**：

```javascript
const stompClient = Stomp.over(new SockJS('/stomp-ws'));

stompClient.connect(
    { Authorization: 'Bearer eyJhbGciOiJIUzI1NiJ9...' },  // JWT Token
    function(frame) {
        console.log('连接成功，用户身份已绑定');
    },
    function(error) {
        console.error('连接失败:', error);
    }
);
```

#### 自定义认证方式

如需接入其他认证系统，修改 `extractUserFromToken` 方法：

```java
private String extractUserFromToken(String token) {
    // 方式1：JWT 解析
    return jwtDecoder.decode(token).getSubject();
    
    // 方式2：调用用户中心验证
    return userCenterClient.validateToken(token).getUserId();
    
    // 方式3：Redis Session 验证
    return redisTemplate.opsForValue().get("session:" + token);
}
```

---

### 2. 消息确认机制 (ACK)

#### 设计目标

解决以下场景的消息丢失问题：
- 网络抖动导致消息未送达
- 客户端崩溃未处理消息
- 多设备同步确保每个设备都收到

#### 工作流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                        消息发送阶段                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  sendMessageWithAck(message)                                        │
│         │                                                           │
│         ├──────────────────────────────────────────────────────┐    │
│         │                                                      │    │
│         ▼                                                      ▼    │
│  ┌─────────────────┐                              ┌─────────────────┐
│  │ 推送到 WebSocket │                              │ 创建 ACK 记录    │
│  │ /user/queue/msg │                              │ Redis Hash      │
│  └────────┬────────┘                              └────────┬────────┘
│           │                                                │        │
│           │  Key: stomp::pending_messages                  │        │
│           │  Field: {msgId}::{sessionId}                   │        │
│           │  Value: NotifyMessage 对象                      │        │
│           │                                                │        │
└───────────┼────────────────────────────────────────────────┼────────┘
            │                                                │
            ▼                                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端处理阶段                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  客户端收到消息                                                       │
│         │                                                           │
│         ├─> 处理业务逻辑（需做幂等处理）                                 │
│         │                                                           │
│         └─> 发送 ACK 确认                                            │
│              stompClient.send('/app/ack', {}, messageId)            │
│                     │                                               │
│                     ▼                                               │
│              服务端收到 ACK                                           │
│                     │                                               │
│                     └─> 从 Redis 移除 ACK 记录                        │
│                         log: [ACK-REDIS] 确认成功                     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
            │
            │ 如果客户端未发送 ACK
            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        定时重试阶段（每 5 秒）                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  @Scheduled(fixedDelay = 5000)                                      │
│  retryRedisMessages()                                               │
│         │                                                           │
│         ├─> 遍历 Redis 中所有待确认消息                                │
│         │                                                           │
│         ├─> 检查条件：                                                │
│         │   • 创建时间 > 5秒（等待窗口）                                │
│         │   • 重试次数 < 12次                                         │
│         │   • 总时长 < 60秒                                           │
│         │   • 目标 Session 仍在线                                     │
│         │                                                           │
│         ├─> 符合条件：重新发送消息，retryCount++                        │
│         │                                                           │
│         └─> 超过限制：移除记录，log: [ACK-REDIS] 消息过期                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### 配置参数

| 参数 | 默认值 | 说明 | 修改位置 |
|------|--------|------|----------|
| `ACK_CHECK_WAIT_MS` | 5000ms | 首次检查等待时间 | `StompWebSocketHandler.java:38` |
| `ACK_RETRY_INTERVAL_MS` | 5000ms | 重试间隔 | `StompWebSocketHandler.java:39` |
| `ACK_MESSAGE_TTL_MS` | 60000ms | 消息最大存活时间 | `StompWebSocketHandler.java:40` |
| `ACK_MAX_RETRY_COUNT` | 12 | 最大重试次数（自动计算） | `StompWebSocketHandler.java:41` |
| `enableLocalAck` | false | 是否使用本地缓存 | `StompWebSocketHandler.java:44` |

#### 核心代码

**发送带 ACK 的消息**：

```java
public void sendMessageWithAck(NotifyMessage message) {
    SimpUser user = userRegistry.getUser(message.getReceiver());
    if (user != null && user.hasSessions()) {
        // 为用户的每个会话（多设备）分别创建 ACK 记录
        for (SimpSession session : user.getSessions()) {
            // 1. 发送消息到指定 Session
            sendMessage(message, session.getId());
            // 2. 创建 ACK 追踪记录
            addAckMessageRecord(message, session.getId());
        }
    }
}

private void addToRedis(NotifyMessage message, String sessionId) {
    String ackKey = buildAckKey(message.getId(), sessionId);  // msgId::sessionId
    RMap<String, NotifyMessage> map = redisson.getMap(PENDING_MAP_KEY);
    
    message.setCreated(System.currentTimeMillis());
    message.setAckRetryCount(0);
    message.setAckLastSent(System.currentTimeMillis());
    
    map.put(ackKey, message);
    log.info("[ACK-REDIS] 消息入队, msgId {}, sessionId {}, receiver {}",
        message.getId(), sessionId, message.getReceiver());
}
```

**客户端 ACK 处理**：

```javascript
stompClient.subscribe('/user/queue/msg', function(message) {
    const data = JSON.parse(message.body);
    
    // 1. 幂等处理（防止重复消息）
    if (processedIds.has(data.id)) {
        stompClient.send('/app/ack', {}, data.id);  // 仍需发送 ACK
        return;
    }
    processedIds.add(data.id);
    
    // 2. 处理业务逻辑
    handleNotification(data);
    
    // 3. 发送 ACK 确认（重要！）
    stompClient.send('/app/ack', {}, data.id);
});
```

#### 监控日志

```
# 消息入队
[ACK-REDIS] 消息入队, msgId msg-123, sessionId sess-456, receiver user-789

# 确认成功
[ACK-REDIS] 确认成功, msgId msg-123, sessionId sess-456, retryCount 0

# 重试发送
[ACK-REDIS] 重发, msgId msg-123, sessionId sess-456, retryCount 3

# 定时任务汇总
[ACK-REDIS] 定时处理完成, total 10, retried 2, expired 1, not online 0
```

---

### 3. 集群消息推送

#### 问题场景

在多节点部署时，用户可能连接到任意节点：

```
用户 A 连接到 ──> 节点 1
用户 B 连接到 ──> 节点 2

业务系统调用节点 1 发送消息给用户 B
问题：节点 1 没有用户 B 的 WebSocket 会话，如何推送？
```

#### 解决方案

使用 Redis Pub/Sub 实现跨节点消息转发：

```
┌─────────────────────────────────────────────────────────────────────┐
│                           业务调用                                   │
│                                                                     │
│  notifyManager.saveAndPublish(message)  // 发送给用户 B               │
│         │                                                           │
└─────────┼───────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         节点 1 处理                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  StompNotifyEventListener.handler(event)                            │
│         │                                                           │
│         ├─> 检查本地是否有用户 B 的会话                                 │
│         │   hasSession("userB") == false                            │
│         │                                                           │
│         └─> 发布事件到 Redis Topic                                    │
│             topic.publish(NotifyMessageEvent)                       │
│                     │                                               │
│                     │  Topic: stomp::ws_notify_topic                │
│                     │                                               │
└─────────────────────┼───────────────────────────────────────────────┘
                      │
                      │ Redis Pub/Sub 广播到所有订阅节点
                      │
          ┌───────────┴───────────┐
          │                       │
          ▼                       ▼
┌─────────────────────┐ ┌─────────────────────┐
│      节点 1         │ │      节点 2         │
│                     │ │                     │
│  收到事件           │ │  收到事件           │
│  hasSession = false │ │  hasSession = true  │
│  忽略               │ │                     │
│                     │ │  sendMessageWithAck │
│                     │ │  推送给用户 B       │
└─────────────────────┘ └─────────────────────┘
```

#### 核心代码

**事件监听器** (`StompNotifyEventListener.java`)：

```java
@Component
public class StompNotifyEventListener {

    private static final String NOTIFY_TOPIC = "stomp::ws_notify_topic";

    @Autowired
    private StompWebSocketHandler stompWebSocketHandler;
    @Autowired
    private Redisson redisson;

    // 监听本地 Spring 事件
    @Async
    @TransactionalEventListener
    public void handler(NotifyMessageEvent event) {
        handlerEvent(event, true);  // isLocalEvent = true
    }

    private void handlerEvent(NotifyMessageEvent event, boolean isLocalEvent) {
        NotifyMessage msg = event.notifyMessageLog().convert();
        
        if (isLocalEvent) {
            // 本地事件：广播到 Redis，让集群所有节点都能处理
            publishClusterEvent(event);
            return;
        }

        // 集群事件：检查本地是否有目标用户的会话
        if (stompWebSocketHandler.hasSession(msg.getReceiver())) {
            log.debug("本地有会话，直接推送, msgId: {}, receiver: {}", 
                msg.getId(), msg.getReceiver());
            stompWebSocketHandler.sendMessageWithAck(msg);
        } else {
            log.debug("本地无会话，忽略, msgId: {}, receiver: {}",
                msg.getId(), msg.getReceiver());
        }
    }

    // 发布到 Redis Topic
    private void publishClusterEvent(NotifyMessageEvent event) {
        RTopic topic = redisson.getTopic(NOTIFY_TOPIC);
        topic.publish(event);
    }

    // 启动时订阅 Redis Topic
    @PostConstruct
    public void subscribeToTopic() {
        RTopic topic = redisson.getTopic(NOTIFY_TOPIC);
        topic.addListener(NotifyMessageEvent.class, 
            (channel, event) -> handlerEvent(event, false));  // isLocalEvent = false
    }
}
```

---

### 4. 消息持久化

#### 持久化流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                        NotifyManager.saveAndPublish()               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  @Transactional                                                     │
│  public NotifyMessageLog saveAndPublish(NotifyMessageLog msg) {     │
│         │                                                           │
│         ├─> 1. 类型校验                                              │
│         │   NotifyType.valueOf(msg.getType()).checkDataType(data)   │
│         │                                                           │
│         ├─> 2. 持久化到数据库                                         │
│         │   notifyMessageLogRepository.save(msg)                    │
│         │   • 自动生成雪花 ID                                         │
│         │   • 自动填充 created/lastModified                          │
│         │                                                           │
│         └─> 3. 发布事件（触发 WebSocket 推送）                         │
│             SpringContextUtil.publishEvent(new NotifyMessageEvent)  │
│                                                                     │
│  }                                                                  │
└─────────────────────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        数据库表结构                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  CREATE TABLE notify_log (                                          │
│      id           VARCHAR(32) PRIMARY KEY,  -- 雪花ID，前缀: ntf_     │
│      type         VARCHAR(32) NOT NULL,     -- 消息类型              │
│      receiver     VARCHAR(64) NOT NULL,     -- 接收者用户ID          │
│      data         JSON,                     -- 消息内容              │
│      viewed       BOOLEAN DEFAULT FALSE,    -- 是否已读              │
│      version      BIGINT DEFAULT 0,         -- 乐观锁版本            │
│      created      BIGINT,                   -- 创建时间戳            │
│      last_modified BIGINT,                  -- 最后修改时间戳         │
│                                                                     │
│      INDEX idx_receiver_viewed_created (receiver, viewed, created)  │
│  );                                                                 │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### 核心代码

**消息实体** (`NotifyMessageLog.java`)：

```java
@Entity
@Table(name = "notify_log", indexes = {
    @Index(columnList = "receiver, viewed, created")
})
public class NotifyMessageLog extends Entity {

    @Id
    @GeneratedValue(generator = "prefixed-id-generator")
    @GenericGenerator(name = "prefixed-id-generator",
        parameters = @Parameter(name = "prefix", value = "ntf_"),
        type = CustomizedIdGenerator.class)
    private String id;

    @Column(nullable = false)
    private String type = NotifyType.STRING_MSG.name();

    @Column(nullable = false)
    private String receiver;

    @Convert(converter = ObjectToJsonConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Object data;

    private boolean viewed = false;

    // 转换为 WebSocket 消息格式
    public NotifyMessage convert() {
        return NotifyMessage.builder()
            .id(getId())
            .type(getType())
            .data(getData())
            .receiver(getReceiver())
            .viewed(isViewed())
            .created(getCreated())  // 继承自 Entity 基类
            .build();
    }
}
```

**历史消息查询**：

```java
// 获取用户的历史消息（按创建时间倒序）
public Page<NotifyMessageLog> getHistoryNotifyByCreated(
    String userId, long created, PageRequest page) {
    return repository.findByReceiverAndCreatedLessThanOrderByCreatedDesc(
        userId, created, page);
}

// 标记消息已读
@Transactional
public void markMessagesAsRead(String userId, List<String> ids) {
    repository.updateViewedTrueByReceiverAndIdIn(userId, ids);
    
    // 推送已读状态变更通知（同步到其他设备）
    NotifyMessageLog notify = NotifyMessageLog.builder()
        .type(NotifyType.NOTIFY_VIEWED.name())
        .data(NotifyType.NotifyUpdateRsp.builder().ids(ids).build())
        .receiver(userId)
        .build();
    publish(notify);  // 不持久化，仅推送
}
```

---

## 接入指南

### 场景：订单状态变更通知

以下示例展示如何在你的项目中接入 Quick-Notify，实现订单状态变更的实时推送。

#### Step 1：定义消息类型

```java
// 1. 定义订单状态消息结构
@Data
@Builder
public class OrderStatusData implements Serializable {
    private String orderId;
    private String status;      // PAID, SHIPPED, DELIVERED
    private String description;
    private LocalDateTime updateTime;
}

// 2. 在 NotifyType 中注册新类型
public enum NotifyType {
    STRING_MSG(String.class),
    NOTIFY_VIEWED(NotifyUpdateRsp.class),
    NOTIFY_DELETED(NotifyUpdateRsp.class),
    
    // 新增：订单状态通知
    ORDER_STATUS(OrderStatusData.class);

    private final Class<?> dataClass;
    
    NotifyType(Class<?> dataClass) {
        this.dataClass = dataClass;
    }

    public void checkDataType(Object data) {
        if (data != null && !this.dataClass.isInstance(data)) {
            throw new IllegalArgumentException("数据类型不匹配: " + this.name());
        }
    }
}
```

#### Step 2：业务代码发送通知

```java
@Service
public class OrderService {

    @Autowired
    private NotifyManager notifyManager;

    /**
     * 订单状态变更时发送通知
     */
    @Transactional
    public void updateOrderStatus(String orderId, String userId, String newStatus) {
        // 1. 更新订单状态（你的业务逻辑）
        orderRepository.updateStatus(orderId, newStatus);
        
        // 2. 构建通知消息
        OrderStatusData data = OrderStatusData.builder()
            .orderId(orderId)
            .status(newStatus)
            .description(getStatusDescription(newStatus))
            .updateTime(LocalDateTime.now())
            .build();
        
        NotifyMessageLog message = NotifyMessageLog.builder()
            .receiver(userId)                      // 接收者用户ID
            .type(NotifyType.ORDER_STATUS.name())  // 消息类型
            .data(data)                            // 消息内容
            .viewed(false)                         // 未读状态
            .build();
        
        // 3. 保存并推送（一行代码搞定）
        notifyManager.saveAndPublish(message);
        
        log.info("订单状态通知已发送, orderId: {}, userId: {}, status: {}", 
            orderId, userId, newStatus);
    }
}
```

#### Step 3：完整消息流转过程

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. 业务调用                                                          │
│    orderService.updateOrderStatus("ORD_001", "user_123", "SHIPPED") │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. NotifyManager.saveAndPublish()                                   │
│                                                                     │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 2.1 类型校验                                                 │  │
│    │     NotifyType.ORDER_STATUS.checkDataType(OrderStatusData)  │  │
│    │     ✓ 通过                                                   │  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                    │                                │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 2.2 持久化到数据库                                            │  │
│    │     INSERT INTO notify_log VALUES (                         │  │
│    │       id = 'ntf_1234567890',                                │  │
│    │       type = 'ORDER_STATUS',                                │  │
│    │       receiver = 'user_123',                                │  │
│    │       data = '{"orderId":"ORD_001","status":"SHIPPED",...}',│  │
│    │       viewed = false,                                       │  │
│    │       created = 1704067200000                               │  │
│    │     )                                                       │  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                    │                                │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 2.3 发布 Spring 事件                                         │  │
│    │     SpringContextUtil.publishEvent(NotifyMessageEvent)      │  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. StompNotifyEventListener.handler() [异步执行]                     │
│                                                                     │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 3.1 发布到 Redis Topic                                       │  │
│    │     topic: stomp::ws_notify_topic                           │  │
│    │     payload: NotifyMessageEvent                             │  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                    │                                │
│                     ┌──────────────┴──────────────┐                 │
│                     │ Redis Pub/Sub 广播          │                  │
│                     └──────────────┬──────────────┘                 │
│              ┌─────────────────────┼─────────────────────┐          │
│              ▼                     ▼                     ▼          │
│    ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐  │
│    │ 节点1 收到事件   │   │ 节点2 收到事件   │   │ 节点N 收到事件   │  │
│    │ hasSession?     │   │ hasSession?     │   │ hasSession?     │  │
│    │ ✗ 忽略          │   │ ✓ 有会话        │   │ ✗ 忽略          │  │
│    └─────────────────┘   └────────┬────────┘   └─────────────────┘  │
│                                   │                                 │
└───────────────────────────────────┼─────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. StompWebSocketHandler.sendMessageWithAck() [节点2执行]            │
│                                                                     │
│    用户 user_123 有 2 个在线会话（手机 + 电脑）                          │
│                                                                     │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 4.1 Session 1 (手机)                                         │  │
│    │     • 发送消息到 /user/user_123/queue/msg                     │  │
│    │     • 创建 ACK 记录: ntf_1234567890::sess_mobile             │  │
│    │     • log: [ACK-REDIS] 消息入队                              │  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 4.2 Session 2 (电脑)                                         │  │
│    │     • 发送消息到 /user/user_123/queue/msg                     │  │
│    │     • 创建 ACK 记录: ntf_1234567890::sess_desktop            │  │
│    │     • log: [ACK-REDIS] 消息入队                              │  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. 客户端接收并确认                                                    │
│                                                                     │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 5.1 手机端收到消息                                            │  │
│    │     stompClient.subscribe('/user/queue/msg', msg => {       │  │
│    │       // 收到: {id:'ntf_123..', type:'ORDER_STATUS', ...}   │  │
│    │       showToast('您的订单已发货');                            │  │
│    │       stompClient.send('/app/ack', {}, 'ntf_1234567890');   │  │
│    │     });                                                     │  │
│    │                                                             │  │
│    │     服务端: [ACK-REDIS] 确认成功, msgId ntf_123.., sess_mobile│  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ 5.2 电脑端收到消息                                            │  │
│    │     // 同样流程...                                           │  │
│    │                                                             │  │
│    │     服务端: [ACK-REDIS] 确认成功, msgId ntf_123.., sess_desk  │  │
│    └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│    ✓ 所有 ACK 记录已清理，消息投递完成                                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### Step 4：前端处理不同消息类型

```javascript
stompClient.subscribe('/user/queue/msg', function(message) {
    const data = JSON.parse(message.body);
    
    // 根据消息类型分发处理
    switch (data.type) {
        case 'ORDER_STATUS':
            handleOrderStatus(data.data);
            break;
        case 'STRING_MSG':
            handleStringMessage(data.data);
            break;
        case 'NOTIFY_VIEWED':
            handleViewedSync(data.data.ids);
            break;
        default:
            console.warn('未知消息类型:', data.type);
    }
    
    // 发送 ACK（所有类型都需要）
    stompClient.send('/app/ack', {}, data.id);
});

function handleOrderStatus(orderData) {
    // orderData = { orderId, status, description, updateTime }
    showNotification({
        title: '订单状态更新',
        body: `订单 ${orderData.orderId} ${orderData.description}`,
        icon: getStatusIcon(orderData.status)
    });
    
    // 更新页面上的订单状态
    updateOrderUI(orderData.orderId, orderData.status);
}
```

---

## API 文档

### REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/vh-stomp-wsend/push_all_obj` | 广播消息 |
| POST | `/vh-stomp-wsend/push_all_obj/{userId}` | 发送给指定用户 |
| POST | `/vh-stomp-wsend/cluster/notify/{userId}` | 集群消息（持久化） |
| GET | `/api/notify/history` | 获取历史消息 |
| POST | `/api/notify/viewed` | 标记消息已读 |
| DELETE | `/api/notify/delete` | 删除消息 |

### STOMP 路径

| 路径 | 类型 | 说明 |
|------|------|------|
| `/user/queue/msg` | 订阅 | 接收个人消息 |
| `/topic/messages` | 订阅 | 接收广播消息 |
| `/app/sendMessage` | 发送 | 发送消息到广播 |
| `/app/ack` | 发送 | 消息确认 |

### 消息格式

```json
{
  "id": "ntf_1234567890",
  "type": "ORDER_STATUS",
  "receiver": "user_123",
  "data": {
    "orderId": "ORD_001",
    "status": "SHIPPED",
    "description": "您的订单已发货",
    "updateTime": "2024-01-01T12:00:00"
  },
  "viewed": false,
  "created": 1704067200000
}
```

---

## 常见问题

### Q: 客户端收到重复消息怎么办？

由于 ACK 重试机制，网络波动时可能收到重复消息。客户端应基于消息 ID 实现幂等处理：

```javascript
const processedIds = new Set();

function handleMessage(data) {
    if (processedIds.has(data.id)) {
        return; // 跳过重复消息
    }
    processedIds.add(data.id);
    // 处理消息...
}
```

### Q: 如何扩展消息类型？

参考上面「接入指南」的 Step 1，在 `NotifyType` 枚举中添加新类型即可。

### Q: 消息发送失败如何处理？

`saveAndPublish` 方法是事务性的，如果数据库保存失败会自动回滚。如果需要自定义错误处理：

```java
try {
    notifyManager.saveAndPublish(message);
} catch (Exception e) {
    log.error("消息发送失败", e);
    // 记录失败日志或加入重试队列
}
```

---

## 许可证

MIT License

---

## 贡献

欢迎提交 Issue 和 Pull Request！
