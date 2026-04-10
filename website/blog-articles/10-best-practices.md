---
title: 【最佳实践】从入门到生产的问题解决汇总
date: 2026-04-11
tags: [最佳实践, FAQ, 问题解决, 生产环境]
description: 汇总 Quick-Notify 实际使用中的常见问题与解决方案，涵盖开发、测试、生产各阶段。
---

## 前言

本文汇总了 Quick-Notify 实际使用中的常见问题与解决方案，帮助开发者快速定位和解决问题。

## 一、开发阶段问题

### 1.1 启动报错

#### ❌ 错误：Port 端口被占用

```
***************************
APPLICATION FAILED TO START
***************************
Description:
Web server failed to start. Port 8080 was already in use.
```

**解决方案**：

```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -i :8080
kill -9 <PID>
```

#### ❌ 错误：Redis 连接失败

```
Could not resolve placeholder 'spring.redis.host' in value "${spring.redis.host}"
```

**解决方案**：

1. 确认 Redis 已启动：
```bash
docker ps | grep redis
```

2. 检查配置：
```yaml
redisson:
  single-server-config:
    address: redis://127.0.0.1:6379
```

### 1.2 编译错误

#### ❌ 错误：Lombok 不生效

```
java: cannot find symbol
  symbol: variable log
```

**解决方案**：确保 pom.xml 配置了 Lombok 注解处理器：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>1.18.34</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### 1.3 WebSocket 连接问题

#### ❌ 错误：404 Not Found

```
GET http://localhost:8080/stomp-ws/info 404 (Not Found)
```

**排查步骤**：

1. 检查是否引入了 `quick-notify-spring-boot-starter`
2. 检查启动类是否在 `io.stu` 包下
3. 检查日志是否显示 `StompWebsocketConfig Bean` 创建成功

**检查日志**：

```
[QuickNotifyAutoConfiguration] 创建 StompWebsocketConfig Bean
[QuickNotifyAutoConfiguration] 创建 StompWebsocketInterceptor Bean
```

#### ❌ 错误：跨域被阻止

```
Access to fetch at 'http://localhost:8080/stomp-ws/info' from origin 'http://localhost:3000'
has been blocked by CORS policy
```

**解决方案**：

```java
registry.addEndpoint("/stomp-ws")
    .setAllowedOriginPatterns("*");  // 开发环境允许所有
```

⚠️ **生产环境不要使用 `*`**，应指定具体域名。

## 二、消息发送问题

### 2.1 消息发送成功但客户端收不到

**排查步骤**：

```
1. 检查客户端是否订阅了正确的路径
   - 应该是：/user/queue/msg
   - 不应该是：/topic/queue/msg

2. 检查 Token 认证是否通过
   - 查看日志：【userId】用户上线了
   - 如果没有日志，说明认证失败

3. 检查用户是否在线
   - SimpUserRegistry.getUser(userId) != null

4. 检查 Redis 连接
   - 如果使用 Redis ACK，确认 Redis 正常
```

**常见原因**：

| 原因 | 解决方案 |
|------|---------|
| 订阅路径错误 | 改为 `/user/queue/msg` |
| Token 无效 | 重新获取有效 Token |
| 用户不在线 | 检查 hasSession() |
| Redis 不可用 | 检查网络连接 |

### 2.2 ACK 超时

```
[ACK-REDIS] 重发, msgId xxx, retryCount 3
```

**原因分析**：

1. 客户端收到消息但没发送 ACK
2. 客户端处理时间过长（超过 5 秒）
3. 网络延迟导致 ACK 未及时到达

**解决方案**：

```java
// 增加 ACK 等待时间
.setAckCheckWaitMs(10000)  // 10 秒

// 或禁用 ACK（不推荐）
.enableLocalAck = true  // 单机模式
```

### 2.3 消息乱序

**原因**：多节点并发处理

**解决方案**：

```javascript
// 客户端实现消息排序
const messageStore = new Map();  // messageId → message

stompClient.subscribe('/user/queue/msg', function(message) {
    const data = JSON.parse(message.body);

    // 按时间戳排序
    if (!messageStore.has(data.id)) {
        messageStore.set(data.id, data);
    }

    // 重新排序并处理
    const sorted = Array.from(messageStore.values())
        .sort((a, b) => a.timestamp - b.timestamp);

    sorted.forEach(msg => processMessage(msg));
});
```

## 三、生产环境问题

### 3.1 连接数突然下降

**排查步骤**：

```bash
# 1. 检查应用日志
grep "用户下线" app.log

# 2. 检查系统资源
top
free -h
df -h

# 3. 检查网络连接
netstat -an | grep :8080 | wc -l

# 4. 检查 Redis 连接
redis-cli -h <host> -p <port> info clients
```

**常见原因**：

| 原因 | 表现 | 解决方案 |
|------|------|---------|
| 内存不足 | OOM | 增加 JVM 内存 |
| 心跳超时 | 大量断连 | 调大心跳间隔 |
| Redis 故障 | 无法连接 | 检查 Redis 状态 |
| 负载过高 | 响应超时 | 扩容 |

### 3.2 消息延迟增加

**排查步骤**：

```bash
# 1. 检查 ACK 重试日志
grep "ACK-REDIS" app.log | tail -100

# 2. 检查 Redis 性能
redis-cli info stats | grep ops

# 3. 检查数据库性能
show processlist;
```

**优化建议**：

1. **增加 ACK 重试间隔**：
```java
.setAckRetryIntervalMs(10000)  // 10 秒
```

2. **增加 Redis 连接池**：
```yaml
redisson:
  connection-pool-size: 128
```

3. **启用消息压缩**：
```yaml
server:
  compression:
    enabled: true
```

### 3.3 内存持续增长

**排查步骤**：

```bash
# 1. 生成堆 dump
jmap -dump:format=b,file=heap.hprof <pid>

# 2. 分析内存泄漏
# 使用 MAT 或 VisualVM 分析
```

**常见原因**：

| 原因 | 表现 | 解决方案 |
|------|------|---------|
| Session 未清理 | 会话数持续增长 | 检查断连处理 |
| 订阅未取消 | 订阅数增长 | unsubscribe() |
| 缓存无限增长 | 缓存数据多 | 添加容量限制 |

## 四、集群问题

### 4.1 消息发送不稳定

**问题**：集群环境下，消息有时能收到，有时收不到

**排查步骤**：

1. 检查所有节点的 Redis 连接
2. 检查 Pub/Sub 订阅状态
3. 检查负载均衡配置

```bash
# 检查 Redis Pub/Sub 状态
redis-cli PUBSUB NUMSUB stomp::ws_notify_topic
```

### 4.2 节点间不同步

**问题**：节点 A 发的消息，节点 B 收不到

**原因**：Redis 连接池耗尽

**解决方案**：

```yaml
redisson:
  connection-pool-size: 64
  connection-minimum-idle-size: 24
```

## 五、客户端问题

### 5.1 浏览器端连接失败

**常见错误**：

```
SockJS connection failed
```

**排查步骤**：

```javascript
const stompClient = Stomp.over(new SockJS('/stomp-ws'));

// 启用调试
stompClient.debug = function(str) {
    console.log(str);
};
```

### 5.2 移动端频繁断连

**原因**：移动网络不稳定，切换网络

**解决方案**：

1. 增加心跳间隔
2. 实现自动重连
3. 优化断连处理

```javascript
let reconnectAttempts = 0;
const maxReconnectAttempts = 10;

function connect() {
    stompClient.connect({}, function() {
        reconnectAttempts = 0;
    }, function(error) {
        reconnectAttempts++;
        if (reconnectAttempts < maxReconnectAttempts) {
            setTimeout(connect, 5000);  // 5 秒后重连
        }
    });
}
```

### 5.3 iOS Safari 问题

**问题**：iOS Safari 切换后台后断开

**解决方案**：

```javascript
// iOS Safari 需要保持后台运行
document.addEventListener('visibilitychange', function() {
    if (document.visibilityState === 'visible') {
        // 恢复到前台，检测是否断连
        if (!stompClient.connected) {
            connect();
        }
    }
});
```

## 六、安全问题

### 6.1 Token 被盗用

**问题**：发现异常连接

**排查步骤**：

```bash
# 检查日志
grep "Token 验证失败" app.log
grep "连接数超限" app.log
```

**解决方案**：

1. 短期 Token + 自动刷新
2. 记录异常 IP 并封禁
3. 增加登录设备限制

### 6.2 CSRF 攻击

**解决方案**：

1. 验证 Origin 头
2. 使用 CSRF Token
3. 生产环境禁用 `*` 跨域

## 七、监控与告警

### 7.1 关键监控指标

| 指标 | 阈值 | 处理 |
|------|------|------|
| 在线用户数 | 突然下降 > 20% | 告警 |
| 待确认消息数 | > 1000 | 告警 |
| ACK 重试率 | > 10% | 排查 |
| 消息延迟 | > 1s | 排查 |

### 7.2 告警配置

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

      - alert: ConnectionDrop
        expr: rate(websocket_disconnect_total[5m]) > 0.1
        for: 1m
        labels:
          severity: critical
```

## 八、调试工具

### 8.1 WebSocket 测试页面

Quick-Notify 自带了测试页面：

```
http://localhost:8080/stomp-websocket-sockjs.html
```

### 8.2 STOMP CLI

```bash
# 安装 stomp-cli
npm install -g stomp-cli

# 连接测试
stomp -u ws://localhost:8080/stomp-ws -s

# 订阅消息
SUBSCRIBE /user/queue/msg
```

### 8.3 Redis CLI

```bash
# 查看待确认消息
redis-cli KEYS "stomp::pending_messages*"

# 查看消息数量
redis-cli SCARD stomp::pending_messages

# 订阅主题
redis-cli SUBSCRIBE stomp::ws_notify_topic
```

## 九、检查清单

```
┌─────────────────────────────────────────────────────────────────┐
│                  Quick-Notify 上线检查清单                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ Redis 正常连接                                               │
│  ✅ MySQL 正常连接                                               │
│  ✅ 日志级别设置正确（生产 INFO）                                 │
│  ✅ CORS 配置生产域名                                            │
│  ✅ Token 认证已启用                                             │
│  ✅ 连接数限制已启用                                             │
│  ✅ 监控告警已配置                                               │
│  ✅ 日志收集已配置                                               │
│  ✅ 心跳配置合理（10秒/30秒断连）                                │
│  ✅ ACK 参数配置合理（5秒/60秒TTL）                             │
│  ✅ 内存/JVM 参数已调优                                          │
│  ✅ 安全配置已检查                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 十、总结

本文汇总了 Quick-Notify 常见问题与解决方案：

- **开发阶段**：启动、编译、连接问题
- **消息发送**：ACK 超时、消息乱序
- **生产环境**：连接下降、延迟增加、内存泄漏
- **集群问题**：同步、不稳定
- **客户端问题**：断连、重连
- **安全问题**：Token、CSRF

---

**完整文档**：请查看 [blog](./) 目录下的其他文章。
