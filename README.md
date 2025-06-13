# Spring Boot3 + WebSocket STOMP + 集群会话 + token认证 集成

## Demo:
* demo html(本地浏览器点开) :  src/main/resources/stomp-websocket-sockjs.html
* demo截图 :
![img.png](img.png)


## 概述
本项目展示了如何在Spring Boot应用程序中集成WebSocket和STOMP。它包括了启用实时通信、用户认证和消息广播所需的基本配置和组件。

## 核心配置类

```
src/main/java/io/stu/notify/stomp
├── NotifyMessage.java
├── NotifyType.java
├── StompWebSocketHandler.java
├── StompWebsocketConfig.java
└── StompWebsocketInterceptor.java
```

### StompWebsocketConfig
- **功能**: 配置WebSocket端点和消息代理。
- **设计**: 实现了`WebSocketMessageBrokerConfigurer`接口，用于设置STOMP端点、消息代理和拦截器。

### StompWebsocketInterceptor
- **功能**: 基于WebSocket连接中的令牌对用户进行认证，基于用户id注册用户会话。
- **设计**: 实现了`ChannelInterceptor`接口，用于拦截和认证传入的WebSocket消息。

### StompWebSocketHandler
- **功能**: 管理向特定用户发送消息或向所有连接的客户端广播消息。
- **设计**: 使用`SimpMessagingTemplate`发送消息，使用`SimpUserRegistry`管理用户会话。

### NotifyMessage
- **功能**: 表示WebSocket通信中使用的自定义消息结构。
- **设计**: 一个简单的POJO，包含消息ID、类型、接收者、数据和状态字段。

### NotifyType
- **功能**: 定义不同类型的通知。
- **设计**: 一个枚举，包含不同消息类型关联的数据类。


## 示例用法
- **广播消息**：
  ```java
  webSocketHandler.broadcastMessage(new NotifyMessage("messageId", "messageData"));
  ```
- **向特定用户发送消息**：
  ```java
  webSocketHandler.sendMessage(new NotifyMessage("messageId", "receiverUserId", "messageData"));
  ```


# 集群模式会话广播

## 核心实现

`StompNotifyEventListener` 主要基于StompWebSocketHandler异步发送通知消息，并在集群环境下实现WebSocket会话的广播功能。

### 事件处理流程

1. **本地会话检查**：`handlerEvent` 方法首先检查目标用户的WebSocket会话是否在当前节点
    - 如果会话存在，直接通过 `StompWebSocketHandler` 推送消息
    - 如果会话不存在，则发布该消息给其它节点处理

2. **集群事件发布**：`publishClusterEvent` 方法通过Redisson将事件发布到Redis主题
    - 使用Redisson的分布式主题功能实现跨节点消息传递
    - 所有集群节点都会订阅该主题并接收事件

3. **事件订阅与处理**：`subscribeToTopic` 方法在组件初始化时订阅Redis主题
    - 当其他节点发布事件时，当前节点会接收到事件并调用 `handlerEvent` 方法
    - 通过 `isLocalEvent` 参数区分本地产生的事件和集群接收的事件，如果会话在当前节点，则推送给用户


### 架构流程图

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                             应用服务节点                                          │
└───────────────────────────────────────────────────────────────────────────────┘
│                                                                                   │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                            事件处理流程                                       │  │
│  │  ┌───────────────────┐    ┌────────────────────────────────────────────┐    │  │
│  │  │ NotifyMessageEvent│───▶│ StompNotifyEventListener                    │    │  │
│  │  │ (本地或集群)        │    │                                            │    │  │
│  │  └───────────────────┘     │  ┌──────────────────────────────────────┐  │    │  │
│  │                            │  │  判断会话是否在当前节点                │  │    │  │
│  │                            │  │  ┌─────────────────────┐  ┌─────────┐ │  │    │  │
│  │                            │  │  │  是                 │  │  否    │ │  │    │  │
│  │                            │  │  └───────────┬─────────┘  └────┬────┘ │  │    │  │
│  │                            │  │              │                  │      │  │    │  │
│  │                            │  │  ┌───────────▼─────────┐  ┌────▼─────┐ │  │    │  │
│  │                            │  │  │ 通过StompWebSocket   │   │ 发布到     │ │  │    │  │
│  │                            │  │  │ Handler直接推送消息   │  │ Redis集群给其它节点处理 │ │  │    │  │
│  │                            │  │  └─────────────────────┘  └───────────┘ │  │    │  │
│  │                            │  └──────────────────────────────────────┘  │    │  │
│  │                            └────────────────────────────────────────────┘    │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
```

# 业务集成：消息持久化与推送

## 核心实现类：NotifyManager
- **功能**: 负责消息持久化到数据库，发送异步消息给StompNotifyEventListener做进一步的消息集群推送。

```java
@Transactional(rollbackFor = Throwable.class)
public NotifyMessageLog saveAndPublish(NotifyMessageLog msg) {
    NotifyType.valueOf(msg.getType()).checkDataType(msg.getData());

    // 持久化消息到数据库
    notifyMessageLogRepository.save(msg);
    // 发送异步消息，交由StompNotifyEventListener做集群广播，发送给指定用户
    SpringContextUtil.publishEvent(new NotifyMessageEvent(msg));
    return msg;
}
```

## 结论
本项目为在Spring Boot应用程序中集成WebSocket和STOMP提供了基本设置。它涵盖了用户认证、消息广播和点对点消息传递。你可以根据具体需求扩展此设置。