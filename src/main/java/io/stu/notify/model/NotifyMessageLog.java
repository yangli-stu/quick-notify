package io.stu.notify.model;

import io.stu.common.orm.CustomizedIdGenerator;
import io.stu.common.orm.IdPrefix;
import io.stu.common.orm.ObjectToJsonConverter;
import io.stu.notify.stomp.NotifyMessage;
import io.stu.notify.stomp.NotifyType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Builder
@Entity
@Table(name = "notify_log", indexes =
    {
        @Index(columnList = "receiver, viewed, created")
    })
@AllArgsConstructor
@NoArgsConstructor
public class NotifyMessageLog extends io.stu.common.orm.Entity {

    @Id
    @GeneratedValue(generator = "prefixed-id-generator")
    @GenericGenerator(name = "prefixed-id-generator",
        parameters = @org.hibernate.annotations.Parameter(name = "prefix", value = IdPrefix.NOTIFY_MESSAGE),
        type = CustomizedIdGenerator.class)
    private String id;

    // NotifyType：对应data的实际数据类型
    @JsonProperty("type")
    @Column(name = "type", nullable = false)
    private String type = NotifyType.STRING_MSG.name();

    // 业务数据
    @Convert(converter = ObjectToJsonConverter.class)
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    @JsonProperty("data")
    private Object data;

    // @JsonProperty("sender")
    // private String sender;

    // user id
    @Setter
    @JsonProperty("receiver")
    @Column(name = "receiver", nullable = false)
    private String receiver;

    // 是否已读
    @Column(name = "viewed")
    @JsonProperty("viewed")
    private boolean viewed = false;

    public NotifyMessage convert() {
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
