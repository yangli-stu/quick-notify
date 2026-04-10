package io.stu.notify.event;

import io.stu.notify.stomp.StompWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 消息事件监听器。
 * 处理集群广播和消息推送。
 */
@Slf4j
public class NotifyEventListener {

    private static final String NOTIFY_TOPIC = "stomp::ws_notify_topic";

    private StompWebSocketHandler stompWebSocketHandler;
    private org.redisson.api.RedissonClient redisson;

    @Autowired
    public void setStompWebSocketHandler(StompWebSocketHandler stompWebSocketHandler) {
        this.stompWebSocketHandler = stompWebSocketHandler;
    }

    @Autowired(required = false)
    public void setRedisson(org.redisson.api.RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handler(NotifyMessageEvent event) {
        handlerEvent(event, true);
    }

    private void handlerEvent(NotifyMessageEvent event, boolean isLocalEvent) {
        val msgLog = event.notifyMessageLog();
        if (isLocalEvent) {
            publishClusterEvent(event);
            return;
        }

        // 如果 ws 会话在当前节点，则直接推送
        if (stompWebSocketHandler.hasSession(msgLog.getReceiver())) {
            log.debug("handlerEvent::msg id {}, start notify user {}. type {}",
                    msgLog.getId(), msgLog.getReceiver(), msgLog.getType());
            stompWebSocketHandler.sendMessageWithAck(msgLog.toNotifyMessage());
            log.debug("handlerEvent::msg id {}, end notify user {}. type {}",
                    msgLog.getId(), msgLog.getReceiver(), msgLog.getType());
        } else {
            log.debug("handlerEvent::msg id {}, cur node don't find notify user {}. type {}",
                    msgLog.getId(), msgLog.getReceiver(), msgLog.getType());
        }
    }

    private void publishClusterEvent(NotifyMessageEvent event) {
        val topic = redisson.getTopic(NOTIFY_TOPIC);
        topic.publish(event);
    }

    /**
     * 订阅 Redis Topic（由配置类调用）
     */
    public void subscribeToTopic() {
        val topic = redisson.getTopic(NOTIFY_TOPIC);
        topic.addListener(NotifyMessageEvent.class, (charSequence, message) -> handlerEvent(message, false));
    }
}
