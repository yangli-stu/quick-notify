package io.stu.notify;

import io.stu.notify.stomp.NotifyMessage;
import io.stu.notify.stomp.NotifyType;
import io.stu.notify.stomp.StompWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Profile({"dev"})
@Controller
public class HappyPathController {
    @Autowired
    private StompWebSocketHandler webSocketHandler;

    @Autowired
    private NotifyManager notifyManager;

    @MessageMapping("/sendMessage")
    @SendTo("/topic/messages")
    public GenericMessage<String> sendMessage(GenericMessage<String> message) {
        return message;
    }

    @MessageMapping("/ack")
    public String ack2(@Payload String messageId, SimpMessageHeaderAccessor headerAccessor) {
        String receiver = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : null;
        // 获取当前发送ACK的sessionId
        String sessionId = headerAccessor.getSessionId();

        if (StringUtils.isBlank(receiver)) {
            log.warn("异常 Cannot get receiver from WebSocket session for ACK: {}", messageId);
            return "ERROR: Receiver not found";
        }

        if (StringUtils.isBlank(sessionId)) {
            log.warn("异常 Cannot get sessionId from WebSocket session for ACK: {}", messageId);
            return "ERROR: SessionId not found";
        }

        log.debug("[ACK] 收到ACK确认, msgId: {}, receiver: {}, sessionId: {}", messageId, receiver, sessionId);

        boolean acknowledged = webSocketHandler.acknowledge(receiver, messageId, sessionId);
        if (acknowledged) {
            return "success " + messageId;
        } else {
            return "ERROR: Message not found or already acknowledged";
        }
    }

    // 向所有客户端广播业务json消息
    @ResponseBody
    @PostMapping({"/vh-stomp-wsend/push_all_obj", "/vh-stomp-wsend/push_all_obj/{receiver}"})
    public String pushMessageObj(@PathVariable(value = "receiver", required = false) String receiver,
        @RequestBody String message) {

        NotifyMessage msg = NotifyMessage.builder()
            .id("xxxxx")
            .receiver(receiver)
            .data(message)
            .type(NotifyType.STRING_MSG.name())
            .viewed(false)
            .build();

        if (StringUtils.isBlank(receiver)) {
            webSocketHandler.broadcastMessage(msg);
        } else {
            webSocketHandler.sendMessageWithAck(msg);
        }
        return "Message pushed successfully";
    }

    @ResponseBody
    @PostMapping("/vh-stomp-wsend/cluster/notify/{receiver}")
    public String notify(@PathVariable(value = "receiver") String receiver,
        @RequestBody String message) throws Exception {

        io.stu.notify.model.NotifyMessageLog msg = io.stu.notify.model.NotifyMessageLog.builder()
            .receiver(receiver)
            .data(message)
            .type(NotifyType.STRING_MSG.name())
            .viewed(false)
            .build();
        notifyManager.saveAndPublish(msg);
        return "Message pushed successfully";
    }

    @ResponseBody
    @GetMapping("/stomp-websocket-sockjs.html")
    public String index() throws IOException {
        return Files.readString(
            Paths.get("src/main/resources/stomp-websocket-sockjs.html"));
    }
}
