package io.stu.notify.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * WebSocket 消息 DTO
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotifyMessage implements Serializable {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    @Builder.Default
    private String type = MessageTypeRegistry.STRING_MSG;

    @Setter
    @JsonProperty("receiver")
    private String receiver;

    @Setter
    @JsonProperty("data")
    private Object data;

    @JsonProperty("viewed")
    @Builder.Default
    private boolean viewed = false;

    @Setter
    @JsonProperty("created")
    private long created;

    @Setter
    @JsonProperty("ack_retry_count")
    @Builder.Default
    private int ackRetryCount = 0;

    @Setter
    @JsonProperty("ack_last_sent")
    @Builder.Default
    private long ackLastSent = 0L;
}
