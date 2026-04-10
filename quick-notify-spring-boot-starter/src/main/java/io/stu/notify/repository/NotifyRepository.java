package io.stu.notify.repository;

import java.util.List;

/**
 * 消息仓储接口。
 * 默认实现为 {@link JdbcNotifyRepository}，用户可实现此接口提供自己的仓储实现。
 */
public interface NotifyRepository {

    /**
     * 保存消息
     *
     * @param msg 消息
     */
    void save(NotifyMessageLog msg);

    /**
     * 分页查询用户消息
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 消息列表
     */
    List<NotifyMessageLog> findByReceiver(String userId, int offset, int limit);

    /**
     * 分页查询用户消息（按创建时间倒序）
     *
     * @param userId  用户ID
     * @param created 时间戳（返回此时间之前的消息）
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 消息列表
     */
    List<NotifyMessageLog> findByReceiverBefore(String userId, long created, int offset, int limit);

    /**
     * 统计用户未读消息数
     *
     * @param userId 用户ID
     * @return 未读消息数
     */
    long countUnread(String userId);

    /**
     * 标记消息为已读
     *
     * @param userId 用户ID
     * @param ids    消息ID列表
     */
    void markAsRead(String userId, List<String> ids);

    /**
     * 删除用户消息
     *
     * @param userId 用户ID
     * @param ids    消息ID列表
     */
    void delete(String userId, List<String> ids);
}
