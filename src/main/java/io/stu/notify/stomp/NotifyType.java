package io.stu.notify.stomp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
public enum NotifyType {
    STRING_MSG(String.class),

    // 消息已读/删除通知
    NOTIFY_VIEWED(NotifyUpdateRsp.class),
    NOTIFY_DELETED(NotifyUpdateRsp.class);

    private final Class<?> dataClass;
    NotifyType(Class<?> dataClass) {
        this.dataClass = dataClass;
    }

    public void checkDataType(Object data) {
        if (data != null && !this.dataClass.isInstance(data)) {
            throw new IllegalArgumentException("NotifyType: 非法参数，数据类型不匹配");
        }
    }

    @Builder
    public record NotifyUpdateRsp(@JsonProperty("ids") List<String> ids) implements Serializable {
    }
}
