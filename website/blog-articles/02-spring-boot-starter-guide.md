---
title: 【接入】Spring Boot Starter 插件化开发实践
date: 2026-04-11
tags: [Spring Boot, Starter, 插件化, 架构]
description: 深入讲解 Quick-Notify 如何实现 Spring Boot Starter 插件化，以及如何自定义配置和扩展功能。
---

## 前言

Spring Boot 的核心优势之一就是**插件化架构**：通过 Starter 机制，开发者可以像搭积木一样快速集成各种功能。本文将深入讲解 Quick-Notify 如何实现 Spring Boot Starter 插件化，以及如何自定义配置和扩展功能。

## 一、Spring Boot 自动配置原理

### 1.1 传统方式 vs Starter 方式

**传统方式**（需要手动配置）：

```java
@Configuration
public class MyWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/stomp-ws").withSockJS();
    }
}
```

**Starter 方式**（一行依赖，零配置）：

```xml
<dependency>
    <groupId>io.stu</groupId>
    <artifactId>quick-notify-spring-boot-starter</artifactId>
</dependency>
```

### 1.2 自动配置原理

Spring Boot 自动配置的核心机制：

```
spring-boot-autoconfigure.jar
    └── META-INF/
        └── spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
                └── com.example.MyAutoConfiguration
```

**`AutoConfiguration.imports` 文件内容**：

```
io.stu.notify.QuickNotifyAutoConfiguration
```

Spring Boot 启动时，会自动加载这个文件中的配置类。

## 二、Quick-Notify Starter 结构

### 2.1 项目结构

```
quick-notify-spring-boot-starter/
├── pom.xml
└── src/main/
    ├── java/io/stu/notify/
    │   ├── QuickNotifyAutoConfiguration.java   # ⭐ 核心自动配置
    │   ├── NotifyManager.java
    │   ├── model/
    │   ├── repository/
    │   ├── event/
    │   └── stomp/
    └── resources/
        ├── META-INF/
        │   └── spring/
        │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        └── schema.sql
```

### 2.2 pom.xml 关键配置

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.stu</groupId>
    <artifactId>quick-notify-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>jar</packaging>

    <!-- Spring Boot 父项目 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.11</version>
    </parent>

    <dependencies>
        <!-- Spring Boot 自动配置 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <optional>true</optional>  <!-- 可选依赖 -->
        </dependency>

        <!-- Spring WebSocket -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Redisson（Redis 客户端）-->
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
            <version>3.37.0</version>
            <optional>true</optional>
        </dependency>

        <!-- 其他依赖... -->
    </dependencies>
</project>
```

## 三、核心自动配置类

### 3.1 QuickNotifyAutoConfiguration

```java
@Configuration
@AutoConfiguration  // Spring Boot 3.x 注解
@EnableAsync         // 启用异步
@Slf4j
public class QuickNotifyAutoConfiguration {

    // ========== Bean 定义 ==========

    /**
     * 消息仓储
     * @ConditionalOnMissingBean: 如果用户已定义，则使用用户的
     */
    @Bean
    @ConditionalOnMissingBean(NotifyRepository.class)
    public NotifyRepository notifyRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcNotifyRepository(jdbcTemplate);
    }

    /**
     * 消息管理器
     */
    @Bean
    public NotifyManager notifyManager(NotifyRepository repository,
                                       ApplicationEventPublisher publisher) {
        return new NotifyManager(repository, publisher);
    }

    /**
     * STOMP 拦截器
     */
    @Bean
    @ConditionalOnMissingBean(ChannelInterceptor.class)
    public StompWebsocketInterceptor stompWebsocketInterceptor(
            ObjectProvider<SimpUserRegistry> userRegistryProvider) {
        return new StompWebsocketInterceptor(userRegistryProvider);
    }

    /**
     * WebSocket 配置
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketMessageBrokerConfigurer.class)
    public WebSocketMessageBrokerConfigurer stompWebsocketConfig() {
        return new StompWebsocketConfig();
    }

    // ... 其他 Bean
}
```

### 3.2 条件注解详解

| 注解 | 作用 |
|------|------|
| `@ConditionalOnMissingBean` | 如果容器中没有该类型的 Bean，才创建 |
| `@ConditionalOnClass` | 如果类路径下有该类，才创建 |
| `@ConditionalOnProperty` | 如果配置文件中配置了该属性，才创建 |

**示例：可选依赖**

```java
@Bean
@ConditionalOnMissingBean
public NotifyEventListener stompNotifyEventListener() {
    NotifyEventListener listener = new NotifyEventListener();
    // Redisson 是可选的，如果用户没配，则跳过 Redis 相关功能
    listener.setRedisson(redisson);
    return listener;
}
```

## 四、用户自定义配置

### 4.1 完全覆盖（用户定义自己的配置）

如果用户需要完全自定义 WebSocket 配置，可以定义自己的 `WebSocketMessageBrokerConfigurer` Bean：

```java
@Configuration
public class MyWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 使用自己的心跳配置
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[]{30000, 30000});
        registry.setApplicationDestinationPrefixes("/my-app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 使用自己的端点
        registry.addEndpoint("/my-websocket")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 使用自己的拦截器
        registration.interceptors(new MyCustomInterceptor());
    }
}
```

**原理**：`@ConditionalOnMissingBean(WebSocketMessageBrokerConfigurer.class)` 会检测到用户已定义该类型的 Bean，从而跳过 Starter 中的默认配置。

### 4.2 部分覆盖（继承并重写）

如果用户只想修改部分配置，可以继承 `StompWebsocketConfig`：

```java
public class MyStompWebsocketConfig extends StompWebsocketConfig {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 调用父类的默认配置
        super.registerStompEndpoints(registry);
        // 添加自定义端点
        registry.addEndpoint("/custom-ws").withSockJS();
    }
}
```

### 4.3 自定义认证逻辑

```java
/**
 * 自定义认证拦截器
 * 继承 StompWebsocketInterceptor 并重写 extractUserId 方法
 */
public class MyAuthInterceptor extends StompWebsocketInterceptor {

    @Override
    protected String extractUserId(StompHeaderAccessor accessor) {
        // 方式1：从 JWT Token 解析
        String token = accessor.getNativeHeader("Authorization").get(0);
        return jwtService.extractUserId(token);

        // 方式2：从 Cookie 获取
        // String sessionId = accessor.getNativeHeader("Cookie").get(0);
        // return sessionService.getUserId(sessionId);

        // 方式3：调用用户中心验证
        // return userCenterClient.validate(token).getUserId();
    }
}
```

注册自定义拦截器：

```java
@Configuration
public class MyWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private MyAuthInterceptor myAuthInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(myAuthInterceptor);
    }
}
```

## 五、配置属性绑定

### 5.1 定义配置属性

```java
// QuickNotifyProperties.java
@ConfigurationProperties(prefix = "quick.notify")
public class QuickNotifyProperties {

    /** WebSocket 端点路径 */
    private String endpoint = "/stomp-ws";

    /** 心跳间隔（毫秒）*/
    private long heartbeatInterval = 10000;

    /** 断开延迟（毫秒）*/
    private long disconnectDelay = 30000;

    /** ACK 检查等待时间（毫秒）*/
    private long ackCheckWaitMs = 5000;

    /** ACK 重试间隔（毫秒）*/
    private long ackRetryIntervalMs = 5000;

    // getters and setters...
}
```

### 5.2 启用属性绑定

```java
@Configuration
@AutoConfiguration
@EnableConfigurationProperties(QuickNotifyProperties.class)
public class QuickNotifyAutoConfiguration {

    @Autowired
    private QuickNotifyProperties properties;

    @Bean
    public WebSocketMessageBrokerConfigurer stompWebsocketConfig() {
        StompWebsocketConfig config = new StompWebsocketConfig();
        config.setHeartbeatInterval(properties.getHeartbeatInterval());
        return config;
    }
}
```

### 5.3 用户配置

```yaml
# application.yml
quick:
  notify:
    endpoint: /my-websocket
    heartbeat-interval: 30000
    ack-retry-interval-ms: 10000
```

## 六、Spring Boot 自动配置加载顺序

### 6.1 加载时机

```
SpringApplication.run()
    ↓
ApplicationContext 初始化
    ↓
ConfigurationClassPostProcessor 处理 @Configuration
    ↓
AutoConfiguration.imports 加载
    ↓
@Conditional 条件筛选
    ↓
Bean 注册
```

### 6.2 加载顺序控制

使用 `@AutoConfigureBefore` 和 `@AutoConfigureAfter` 控制加载顺序：

```java
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class QuickNotifyAutoConfiguration {
    // ...
}
```

## 七、实战：自定义 NotifyRepository

### 7.1 定义接口

```java
public interface NotifyRepository {
    void save(NotifyMessageLog message);
    NotifyMessageLog findById(String id);
    Page<NotifyMessageLog> findByReceiver(String receiver, PageRequest page);
    void updateViewed(String receiver, List<String> ids);
}
```

### 7.2 实现自定义 Repository

```java
@Repository
public class MySqlNotifyRepository implements NotifyRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void save(NotifyMessageLog message) {
        // 使用 MyBatis 或 JdbcTemplate 实现
        jdbcTemplate.update(
            "INSERT INTO notify_log (id, type, receiver, data, viewed, created) VALUES (?, ?, ?, ?, ?, ?)",
            message.getId(), message.getType(), message.getReceiver(),
            message.getData(), message.isViewed(), message.getCreated()
        );
    }

    // ... 其他方法实现
}
```

### 7.3 用户使用自定义实现

由于 `@ConditionalOnMissingBean(NotifyRepository.class)` 的存在，Spring 会优先使用用户定义的 `NotifyRepository` Bean：

```java
@Configuration
public class MyRepositoryConfig {
    @Bean
    public NotifyRepository notifyRepository() {
        return new MySqlNotifyRepository();
    }
}
```

## 八、最佳实践

### 8.1 可选依赖

Starter 中的依赖应标记为 `<optional>true</optional>`：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
    <optional>true</optional>  <!-- 用户需要显式引入 -->
</dependency>
```

### 8.2 默认配置

提供合理的默认值，用户可以通过配置覆盖：

```java
@Bean
@ConditionalOnMissingBean
public StompWebsocketConfig stompWebsocketConfig() {
    StompWebsocketConfig config = new StompWebsocketConfig();
    config.setHeartbeatInterval(10000);  // 默认 10 秒
    config.setDisconnectDelay(30000);    // 默认 30 秒
    return config;
}
```

### 8.3 日志友好

在关键节点打印日志，方便用户排查问题：

```java
log.info("[QuickNotifyAutoConfiguration] 创建 NotifyRepository Bean");
log.info("[QuickNotifyAutoConfiguration] 创建 StompWebsocketConfig Bean");
```

## 九、总结

本文深入讲解了 Quick-Notify Spring Boot Starter 的实现原理：

- ✅ **自动配置机制**：通过 `AutoConfiguration.imports` 自动加载
- ✅ **条件注解**：`@ConditionalOnMissingBean` 支持用户覆盖
- ✅ **可选依赖**：用户按需引入
- ✅ **配置属性绑定**：支持用户自定义配置
- ✅ **扩展性设计**：支持继承和重写

---

## 下一步

- 🔧 [WebSocket STOMP 协议深入理解](./03-websocket-stomp-principle.md)
- ✅ [ACK 确认机制详解](./04-ack-reliability-design.md)
- 🌐 [Redis 集群方案](./05-redis-cluster-solution.md)
