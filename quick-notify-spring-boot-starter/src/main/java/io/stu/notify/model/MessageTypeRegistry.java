package io.stu.notify.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息类型注册中心，替代 NotifyType 枚举。
 * 用户可通过 {@link #register(String, Class)} 注册新的消息类型。
 */
public class MessageTypeRegistry {

    private static final Map<String, Class<?>> TYPES = new ConcurrentHashMap<>();

    public static final String STRING_MSG = "STRING_MSG";
    public static final String NOTIFY_VIEWED = "NOTIFY_VIEWED";
    public static final String NOTIFY_DELETED = "NOTIFY_DELETED";

    static {
        register(STRING_MSG, String.class);
        register(NOTIFY_VIEWED, NotifyUpdateRsp.class);
        register(NOTIFY_DELETED, NotifyUpdateRsp.class);
    }

    /**
     * 注册新的消息类型
     *
     * @param type      类型名称
     * @param dataClass 数据类
     */
    public static void register(String type, Class<?> dataClass) {
        TYPES.put(type, dataClass);
    }

    /**
     * 获取消息类型的数据类
     *
     * @param type 类型名称
     * @return 数据类，未注册返回 null
     */
    public static Class<?> getDataClass(String type) {
        return TYPES.get(type);
    }

    /**
     * 校验数据类型是否匹配
     *
     * @param type 类型名称
     * @param data 数据
     * @throws IllegalArgumentException 如果数据类型不匹配
     */
    public static void checkDataType(String type, Object data) {
        if (data == null) {
            return;
        }
        Class<?> dataClass = TYPES.get(type);
        if (dataClass != null && !dataClass.isInstance(data)) {
            throw new IllegalArgumentException("NotifyType: 非法参数，数据类型不匹配, type=" + type);
        }
    }

    /**
     * 消息已读/删除响应
     */
    public record NotifyUpdateRsp(java.util.List<String> ids) {
    }
}
