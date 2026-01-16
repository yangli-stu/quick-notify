package io.stu.notify.stomp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompWebsocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private StompWebsocketInterceptor userChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(2);
        heartbeatScheduler.setThreadNamePrefix("vh-ws-heartbeat-");
        heartbeatScheduler.initialize();

        registry.enableSimpleBroker("/topic", "/queue").setHeartbeatValue(new long[]{10000, 10000}).setTaskScheduler(heartbeatScheduler); // 订阅路径前缀
        registry.setApplicationDestinationPrefixes("/app"); // 客户端发送给服务端消息路径前缀
        registry.setUserDestinationPrefix("/user"); // 服务器推送给指定用户专属消息路径
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/stomp-ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()
            .setHeartbeatTime(10000) // 客户端心跳发送间隔（10秒）
            .setDisconnectDelay(30000); // 30秒无心跳则主动断开会话
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(userChannelInterceptor);
    }

}