package io.stu.notify.event;

import io.stu.notify.stomp.StompWebSocketHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.redisson.Redisson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class StompNotifyEventListener {

    @Autowired
    private StompWebSocketHandler stompWebSocketHandler;

    @Autowired
    private Redisson redisson;
    private static final String NOTIFY_TOPIC = "stomp::ws_notify_topic";

    @Async
    @TransactionalEventListener
    public void handler(NotifyMessageEvent event) {
        handlerEvent(event, true);
    }

    private void handlerEvent(NotifyMessageEvent event, boolean isLocalEvent) {
        val msgLog = event.notifyMessageLog();
        // 如果ws会话在当前节点，则直接推送
        if (stompWebSocketHandler.hasSession(event.notifyMessageLog().getReceiver())) {
            log.debug("msg id {}, start io.stu.notify user {}. type {}", msgLog.getId(), msgLog.getReceiver(), msgLog.getType());
            stompWebSocketHandler.sendMessage(event.notifyMessageLog().convert());
            log.debug("msg id {}, end io.stu.notify user {}. type {}", msgLog.getId(), msgLog.getReceiver(), msgLog.getType());
            return;
        }

        // ws会话不在当前节点，且是本节点产生的消息，则广播给集群其它节点处理
        if (isLocalEvent) {
            publishClusterEvent(event);
        }
    }

    private void publishClusterEvent(NotifyMessageEvent event) {
        // 获取 Redis 主题，集群广播消息
        val topic = redisson.getTopic(NOTIFY_TOPIC);
        topic.publish(event);
    }

    @PostConstruct
    public void subscribeToTopic() {
        // 获取 Redis 主题，添加消息监听器
        val topic = redisson.getTopic(NOTIFY_TOPIC);
        topic.addListener(NotifyMessageEvent.class, (charSequence, message) -> handlerEvent(message, false));
    }
}
