package io.stu.notify.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * WebSocket 连接拦截器。
 * 用户可继承此类重写 {@link #extractUserId(StompHeaderAccessor)} 或 {@link #preSend(Message, MessageChannel)}。
 */
@Slf4j
public class StompWebsocketInterceptor implements ChannelInterceptor {

    private final ObjectProvider<SimpUserRegistry> userRegistryProvider;

    public StompWebsocketInterceptor(ObjectProvider<SimpUserRegistry> userRegistryProvider) {
        this.userRegistryProvider = userRegistryProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())){
                String userId;
                try {
                    userId = extractUserId(accessor);
                } catch (Exception e) {
                    log.error("token is error {}", e.getMessage(), e);
                    throw new IllegalStateException("The token is illegal");
                }
                if(userId == null || userId.isBlank()){
                    log.error("token is overtime");
                    throw new IllegalStateException("The token is illegal");
                }
                SimpUserRegistry registry = userRegistryProvider.getIfAvailable();
                List<String> existingSessions = Optional.ofNullable(registry != null ? registry.getUser(userId) : null)
                    .map(SimpUser::getSessions)
                    .map(sessions -> sessions.stream().map(SimpSession::getId).toList())
                    .orElse(List.of());
                if (existingSessions.size() > 10) {
                    log.error("user {} connect stomp exceed max limit", userId);
                    throw new IllegalStateException("The user connect stomp exceed max limit");
                }
                accessor.setUser(new MyPrincipal(userId));
                log.info("【{}】用户上线了, 当前新会话：{}, 当前总用户数 {}，已存在会话：{}", userId, accessor.getSessionId(), registry != null ? registry.getUserCount() : -1, existingSessions);
            } else if(StompCommand.DISCONNECT.equals(accessor.getCommand())){
                SimpUserRegistry registry = userRegistryProvider.getIfAvailable();
                log.info("【{}】用户下线了, 当前会话：{}, 当前总用户数 {}", Optional.ofNullable(accessor.getUser()).map(Principal::getName).orElse(null), accessor.getSessionId(), registry != null ? registry.getUserCount() : -1);
            }
        }
        return message;
    }

    /**
     * 从 StompHeaderAccessor 提取用户ID。
     * 子类可重写此方法实现自定义认证逻辑（如从 Cookie、JWT 等获取用户ID）。
     *
     * @param accessor STOMP 头部访问器
     * @return 用户ID
     */
    protected String extractUserId(StompHeaderAccessor accessor) {
        // 默认实现：从 Authorization header 获取
        List<String> headers = accessor.getNativeHeader("Authorization");
        if (headers == null || headers.isEmpty()) {
            throw new IllegalStateException("Authorization header not found");
        }
        String token = headers.get(0);

        // dev 环境：token 直接作为用户ID
        if (token.startsWith("test")) {
            return token;
        }

        // 生产环境：子类重写此方法实现 JWT 解析等逻辑
        return token;
    }

    public static class MyPrincipal implements Principal {
        private final String name;

        public MyPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
