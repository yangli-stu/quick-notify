package io.stu.notify.stomp;

import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 配置。
 * <p>
 * 默认配置：端点 /stomp-ws，心跳 10秒，30秒断连。
 * <p>
 * 用户可覆盖方式：
 * <ul>
 *   <li>定义自己的 {@link WebSocketMessageBrokerConfigurer} Bean</li>
 *   <li>继承此类重写相关方法</li>
 * </ul>
 */
public class StompWebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private ChannelInterceptor channelInterceptor;

    public void setChannelInterceptor(ChannelInterceptor interceptor) {
        this.channelInterceptor = interceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(2);
        heartbeatScheduler.setThreadNamePrefix("quick-notify-ws-heartbeat-");
        heartbeatScheduler.initialize();

        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/stomp-ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setHeartbeatTime(10000)
                .setDisconnectDelay(30000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        if (channelInterceptor != null) {
            registration.interceptors(channelInterceptor);
        }
    }
}
