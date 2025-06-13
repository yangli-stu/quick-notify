package io.stu.notify;

import io.stu.notify.stomp.NotifyMessage;
import io.stu.notify.stomp.NotifyType;
import io.stu.notify.stomp.StompWebSocketHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

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
            webSocketHandler.sendMessage(msg);
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
}
