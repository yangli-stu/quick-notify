package io.stu.notify.stomp;

import io.stu.common.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class StompWebsocketInterceptor implements ChannelInterceptor {
    @Lazy
    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())){
                String userId;
                try {
                    String token = accessor.getNativeHeader("Authorization").get(0);
                    userId = extractUserFromToken(token);
                } catch (Exception e) {
                    log.error("token is error {}", e.getMessage(), e);
                    throw new IllegalStateException("The token is illegal");
                }
                if(StringUtils.isEmpty(userId)){
                    log.error("token is overtime");
                    throw new IllegalStateException("The token is illegal");
                }
                List<String> existingSessions = Optional.ofNullable(simpUserRegistry.getUser(userId))
                    .map(SimpUser::getSessions)
                    .map(sessions -> sessions.stream().map(SimpSession::getId).toList())
                    .orElse(List.of());
                if (existingSessions.size() > 10) {
                    log.error("user {} connect stomp exceed max limit", userId);
                    throw new IllegalStateException("The user connect stomp exceed max limit");
                }
                accessor.setUser(new MyPrincipal(userId));
                log.info("【{}】用户上线了, 当前新会话：{}, 当前总用户数 {}，已存在会话：{}", userId, accessor.getSessionId(), simpUserRegistry.getUserCount(), existingSessions);
            } else if(StompCommand.DISCONNECT.equals(accessor.getCommand())){
                log.info("【{}】用户下线了, 当前会话：{}, 当前总用户数 {}", Optional.ofNullable(accessor.getUser()).map(Principal::getName).orElse(null), accessor.getSessionId(), simpUserRegistry.getUserCount());
            }
        }
        return message;
    }

    private String extractUserFromToken(String token) {
        if (SpringContextUtil.isDevEnv() && token.startsWith("test")) {
            return token;
        }

        return "SpringContextUtil.getBean(JwtDecoder.class).decode(token).getSubject()";
        // return SpringContextUtil.getBean(JwtDecoder.class).decode(token).getSubject();
    }

    public static class MyPrincipal implements Principal {
        private final String name;
        public MyPrincipal(String name){
            this.name=name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

}
