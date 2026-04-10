package io.stu.notify.repository;

import io.stu.notify.model.MessageTypeRegistry;
import io.stu.notify.model.NotifyMessage;
import lombok.Getter;
import lombok.Setter;

/**
 * 消息日志实体。
 * 提供基础字段，具体表结构由 schema.sql 定义。
 */
@Getter
@Setter
public class NotifyMessageLog {

    private String id;
    private String type = MessageTypeRegistry.STRING_MSG;
    private String receiver;
    private Object data;
    private boolean viewed = false;
    private long created;
    private long lastModified;

    public NotifyMessageLog() {
    }

    public NotifyMessageLog(String id, String type, String receiver, Object data) {
        this.id = id;
        this.type = type;
        this.receiver = receiver;
        this.data = data;
        this.created = System.currentTimeMillis();
    }

    /**
     * 转换为 WebSocket 消息
     */
    public NotifyMessage toNotifyMessage() {
        return NotifyMessage.builder()
                .id(getId())
                .type(getType())
                .data(getData())
                .receiver(getReceiver())
                .viewed(isViewed())
                .created(getCreated())
                .build();
    }
}
