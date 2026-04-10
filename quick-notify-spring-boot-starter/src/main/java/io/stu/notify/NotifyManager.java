package io.stu.notify;

import io.stu.notify.event.NotifyMessageEvent;
import io.stu.notify.model.MessageTypeRegistry;
import io.stu.notify.repository.NotifyMessageLog;
import io.stu.notify.repository.NotifyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

/**
 * 消息管理器。
 * 依赖 {@link NotifyRepository} 进行数据持久化。
 */
@Slf4j
public class NotifyManager {

    private final NotifyRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public NotifyManager(NotifyRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 保存并发布消息（持久化 + 推送）
     *
     * @param msg 消息
     * @return 保存的消息
     */
    public NotifyMessageLog saveAndPublish(NotifyMessageLog msg) {
        MessageTypeRegistry.checkDataType(msg.getType(), msg.getData());

        if (msg.getCreated() == 0) {
            msg.setCreated(System.currentTimeMillis());
        }

        repository.save(msg);
        eventPublisher.publishEvent(new NotifyMessageEvent(msg));

        log.debug("消息已保存并发布, id={}, receiver={}, type={}",
                msg.getId(), msg.getReceiver(), msg.getType());
        return msg;
    }

    /**
     * 仅发布消息（不持久化）
     *
     * @param msg 消息
     * @return 发布的消息
     */
    public NotifyMessageLog publish(NotifyMessageLog msg) {
        MessageTypeRegistry.checkDataType(msg.getType(), msg.getData());
        eventPublisher.publishEvent(new NotifyMessageEvent(msg));
        return msg;
    }

    /**
     * 获取用户历史消息（按创建时间倒序）
     *
     * @param userId 用户ID
     * @param created 时间戳（返回此时间之前的消息）
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 消息列表
     */
    public List<NotifyMessageLog> getHistoryNotifyByCreated(String userId, long created, int offset, int limit) {
        return repository.findByReceiverBefore(userId, created, offset, limit);
    }

    /**
     * 标记消息为已读
     *
     * @param userId 用户ID
     * @param ids    消息ID列表
     */
    public void markMessagesAsRead(String userId, List<String> ids) {
        repository.markAsRead(userId, ids);

        // 发布已读状态变更通知
        NotifyMessageLog notify = new NotifyMessageLog();
        notify.setType(MessageTypeRegistry.NOTIFY_VIEWED);
        notify.setReceiver(userId);
        notify.setData(new MessageTypeRegistry.NotifyUpdateRsp(ids));
        publish(notify);
    }

    /**
     * 删除消息
     *
     * @param userId 用户ID
     * @param ids    消息ID列表
     */
    public void deleteByUserId(String userId, List<String> ids) {
        repository.delete(userId, ids);

        // 发布删除状态变更通知
        NotifyMessageLog notify = new NotifyMessageLog();
        notify.setType(MessageTypeRegistry.NOTIFY_DELETED);
        notify.setReceiver(userId);
        notify.setData(new MessageTypeRegistry.NotifyUpdateRsp(ids));
        publish(notify);
    }
}
