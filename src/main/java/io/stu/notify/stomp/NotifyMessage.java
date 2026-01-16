package io.stu.notify.stomp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotifyMessage {

    @JsonProperty("id")
    private String id;

    // NotifyType
    @JsonProperty("type")
    private String type = NotifyType.STRING_MSG.name();

    // @JsonProperty("sender")
    // private String sender;

    // user id
    @Setter
    @JsonProperty("receiver")
    private String receiver;

    @Setter
    @JsonProperty("data")
    private Object data;

    @JsonProperty("viewed")
    private boolean viewed = false;

    @Setter
    @JsonProperty("created")
    private long created;

    @Setter
    @JsonProperty("ack_retry_count")
    private int ackRetryCount = 0;

    @Setter
    @JsonProperty("ack_last_sent")
    private long ackLastSent = 0L;

}
