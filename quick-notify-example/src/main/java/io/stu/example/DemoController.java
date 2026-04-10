package io.stu.example;

import io.stu.notify.NotifyManager;
import io.stu.notify.model.MessageTypeRegistry;
import io.stu.notify.model.NotifyMessage;
import io.stu.notify.repository.NotifyMessageLog;
import io.stu.notify.stomp.StompWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 测试控制器
 */
@Slf4j
@Profile({"dev", "default"})
@Controller
public class DemoController {

    @Autowired
    private StompWebSocketHandler webSocketHandler;

    @Autowired
    private NotifyManager notifyManager;

    @MessageMapping("/sendMessage")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/messages")
    public GenericMessage<String> sendMessage(GenericMessage<String> message) {
        return message;
    }

    @MessageMapping("/ack")
    public String ack(@Payload(required = false) String messageId, SimpMessageHeaderAccessor headerAccessor) {
        String receiver = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : null;
        String sessionId = headerAccessor.getSessionId();

        if (messageId == null || messageId.isBlank()) {
            log.warn("ACK 失败: messageId 为空");
            return "ERROR: messageId is empty";
        }

        if (receiver == null || sessionId == null) {
            log.warn("ACK 失败: receiver 或 sessionId 为空, msgId: {}", messageId);
            return "ERROR: Receiver or sessionId not found";
        }

        log.debug("[ACK] 收到ACK确认, msgId: {}, receiver: {}, sessionId: {}", messageId, receiver, sessionId);
        boolean acknowledged = webSocketHandler.acknowledge(receiver, messageId, sessionId);
        return acknowledged ? "success " + messageId : "ERROR: Message not found or already acknowledged";
    }

    @ResponseBody
    @PostMapping({"/vh-stomp-wsend/push_all_obj", "/vh-stomp-wsend/push_all_obj/{receiver}"})
    public String pushMessageObj(
            @PathVariable(value = "receiver", required = false) String receiver,
            @RequestBody String message) {

        NotifyMessage msg = NotifyMessage.builder()
                .id(java.util.UUID.randomUUID().toString())
                .receiver(receiver)
                .data(message)
                .type(MessageTypeRegistry.STRING_MSG)
                .viewed(false)
                .build();

        if (receiver == null || receiver.isBlank()) {
            webSocketHandler.broadcastMessage(msg);
        } else {
            webSocketHandler.sendMessageWithAck(msg);
        }
        return "Message pushed successfully";
    }

    @ResponseBody
    @PostMapping("/vh-stomp-wsend/cluster/notify/{receiver}")
    public String notify(@PathVariable(value = "receiver") String receiver,
                         @RequestBody String message) {
        NotifyMessageLog msg = new NotifyMessageLog();
        msg.setReceiver(receiver);
        msg.setData(message);
        msg.setType(MessageTypeRegistry.STRING_MSG);
        msg.setViewed(false);
        notifyManager.saveAndPublish(msg);
        return "Message pushed successfully";
    }
}
