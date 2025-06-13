package io.stu.notify.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StompWebSocketHandler {
    @Autowired
    private SimpUserRegistry userRegistry;
    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    public void sendMessage(NotifyMessage message) {
        try {
            SimpUser simpUser = userRegistry.getUser(message.getReceiver());
            if (simpUser == null || !simpUser.hasSessions()) {
                return;
            }
            simpMessagingTemplate.convertAndSendToUser(simpUser.getName(), "/queue/msg", message);
        } catch (Exception e) {
            log.error("Error sending message, id: {}, errorMessage {}", message.getId(), e.getMessage(), e);
        }
    }

    // 广播消息给本节点所有连接的客户端
    public void broadcastMessage(NotifyMessage message) {
        userRegistry.getUsers().forEach(simpUser -> {
            if (simpUser.hasSessions()) {
                message.setReceiver(simpUser.getName());
                sendMessage(message);
            }
        });
    }

    public boolean hasSession(String receiver) {
        SimpUser simpUser = userRegistry.getUser(receiver);
        return simpUser != null && simpUser.hasSessions();
    }
}