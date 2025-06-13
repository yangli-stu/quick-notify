package io.stu.notify;

import io.stu.common.util.SpringContextUtil;
import io.stu.notify.event.NotifyMessageEvent;
import io.stu.notify.model.NotifyMessageLog;
import io.stu.notify.repository.NotifyMessageLogRepository;
import io.stu.notify.stomp.NotifyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class NotifyManager {

    @Autowired
    private NotifyMessageLogRepository notifyMessageLogRepository;

    @Transactional(rollbackFor = Throwable.class)
    public NotifyMessageLog saveAndPublish(NotifyMessageLog msg) {
        NotifyType.valueOf(msg.getType()).checkDataType(msg.getData());

        notifyMessageLogRepository.save(msg);
        SpringContextUtil.publishEvent(new NotifyMessageEvent(msg));
        return msg;
    }

    @Transactional(rollbackFor = Throwable.class)
    public NotifyMessageLog publish(NotifyMessageLog msg) {
        NotifyType.valueOf(msg.getType()).checkDataType(msg.getData());
        SpringContextUtil.publishEvent(new NotifyMessageEvent(msg));
        return msg;
    }

    public Page<NotifyMessageLog> getHistoryNotify(String userId, PageRequest page) {
        return notifyMessageLogRepository.findByReceiverOrderByViewedAscCreatedDesc(userId, page);
    }

    public Page<NotifyMessageLog> getHistoryNotifyByCreated(String userId, long created, PageRequest page) {
        return notifyMessageLogRepository.findByReceiverAndCreatedLessThanOrderByCreatedDesc(userId, created, page);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void markMessagesAsRead(String userId, List<String> ids) {
        notifyMessageLogRepository.updateViewedTrueByReceiverAndIdIn(userId, ids);

        NotifyMessageLog newNotify = NotifyMessageLog.builder()
            .type(NotifyType.NOTIFY_VIEWED.name())
            .data(NotifyType.NotifyUpdateRsp.builder().ids(ids).build())
            .receiver(userId)
            .viewed(false)
            .build();
        publish(newNotify);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void deleteByUserId(String userId) {
        notifyMessageLogRepository.deleteByReceiver(userId);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void deleteAllById(String userId, List<String> ids) {
        notifyMessageLogRepository.deleteByReceiverAndIdIn(userId, ids);

        NotifyMessageLog newNotify = NotifyMessageLog.builder()
            .type(NotifyType.NOTIFY_DELETED.name())
            .data(NotifyType.NotifyUpdateRsp.builder().ids(ids).build())
            .receiver(userId)
            .viewed(false)
            .build();
        publish(newNotify);
    }
}
