package io.stu.notify.stomp;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.Principal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static javax.management.timer.Timer.ONE_MINUTE;
import static javax.management.timer.Timer.ONE_SECOND;

@Slf4j
@Component
public class StompWebSocketHandler {
    @Autowired
    private SimpUserRegistry userRegistry;
    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;
    @Autowired
    private Redisson redisson;

    private static final String PENDING_MAP_KEY = "stomp::pending_messages";
    private static final long ACK_CHECK_WAIT_MS = 5 * ONE_SECOND;
    private static final long ACK_RETRY_INTERVAL_MS = 5 * ONE_SECOND;
    private static final long ACK_MESSAGE_TTL_MS = 1 * ONE_MINUTE;
    private static final long ACK_MAX_RETRY_COUNT = ACK_MESSAGE_TTL_MS / ACK_RETRY_INTERVAL_MS;

    // 本地缓存
    private static final boolean enableLocalAck = false;
    private final ConcurrentHashMap<String, NotifyMessage> localCache = new ConcurrentHashMap<>();

    /**
     * 生成ACK记录的key: messageId::sessionId
     */
    private String buildAckKey(String messageId, String sessionId) {
        return messageId + "::" + sessionId;
    }

    /**
     * 从ACK key中解析messageId和sessionId
     */
    private String[] parseAckKey(String ackKey) {
        int index = ackKey.indexOf("::");
        if (index < 0) {
            return new String[]{ackKey, null};
        }
        return new String[]{ackKey.substring(0, index), ackKey.substring(index + 2)};
    }

    public void sendMessageWithAck(NotifyMessage message) {
        SimpUser user = userRegistry.getUser(message.getReceiver());
        if (user != null && user.hasSessions()) {
            // 为每个session创建ACK记录
            List<SimpSession> sessions = new ArrayList<>(user.getSessions());
            for (SimpSession session : sessions) {
                sendMessage(message, session.getId());
                addAckMessageRecord(message, session.getId());
            }
        } else {
            log.warn("[ACK] 无法创建ACK记录, 用户不存在或无session, msgId: {}, receiver: {}",
                message.getId(), message.getReceiver());
        }
    }

    /**
     * 发送消息到指定session（如果sessionId为null，则发送到所有session）
     * @param message 消息
     * @param sessionId 目标session ID，如果为null则发送到用户的所有session
     */
    public void sendMessage(NotifyMessage message, String sessionId) {
        SimpUser user = userRegistry.getUser(message.getReceiver());
        if (user == null || !user.hasSessions()) {
            log.warn("[ACK] 发送失败, 用户不存在或无 session, msgId: {}, receiver: {}, user: {}",
                message.getId(), message.getReceiver(), user);
            return;
        }

        if (StringUtils.isBlank(sessionId)) {
            // 发送到所有session
            simpMessagingTemplate.convertAndSendToUser(user.getName(), "/queue/msg", message);
            log.debug("[ACK] 发送消息到所有session, msgId: {}, receiver: {}, sessionCount: {}",
                message.getId(), message.getReceiver(), user.getSessions().size());
        } else {
            String destination = "/user/" + user.getName() + "/queue/msg";

            final Principal principal = () -> user.getName();
            simpMessagingTemplate.convertAndSend(destination, message, msg -> {
                SimpMessageHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(msg, SimpMessageHeaderAccessor.class);
                if (accessor != null) {
                    accessor.setSessionId(sessionId);
                    accessor.setUser(principal);
                }
                return msg;
            });

            log.debug("[ACK] 发送消息到指定session, msgId: {}, receiver: {}, sessionId: {}",
                message.getId(), message.getReceiver(), sessionId);
        }
    }

    public void broadcastMessage(NotifyMessage message) {
        userRegistry.getUsers().forEach(user -> {
            if (user.hasSessions()) {
                message.setReceiver(user.getName());
                sendMessage(message, null);
            }
        });
    }

    public boolean hasSession(String receiver) {
        SimpUser user = userRegistry.getUser(receiver);
        return user != null && user.hasSessions();
    }

    /**
     * 消息入队（为指定session创建ACK记录）
     */
    public void addAckMessageRecord(NotifyMessage message, String sessionId) {
        if (message.getId() == null || StringUtils.isBlank(sessionId)) {
            log.warn("[ACK] 无法创建ACK记录, msgId: {}, sessionId: {}", message.getId(), sessionId);
            return;
        }

        if (enableLocalAck) {
            addToLocalCache(message, sessionId);
        } else {
            addToRedis(message, sessionId);
        }
    }

    /**
     * ACK 确认
     * @param receiver 接收者用户ID
     * @param messageId 消息ID
     * @param sessionId 发送ACK的session ID（必需）
     */
    public boolean acknowledge(String receiver, String messageId, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            log.warn("[ACK] ACK确认失败, sessionId为空, msgId: {}, receiver: {}", messageId, receiver);
            return false;
        }
        log.debug("[ACK] 处理ACK确认, msgId: {}, receiver: {}, sessionId: {}", messageId, receiver, sessionId);

        if (enableLocalAck) {
            return ackFromLocalCache(receiver, messageId, sessionId);
        } else {
            return ackFromRedis(receiver, messageId, sessionId);
        }
    }

    // ==================== 本地缓存实现 ====================

    private void addToLocalCache(NotifyMessage message, String sessionId) {
        String ackKey = buildAckKey(message.getId(), sessionId);
        if (localCache.containsKey(ackKey)) {
            log.debug("[ACK-LOCAL] 消息已存在, msgId {}, sessionId {}", message.getId(), sessionId);
            return;
        }
        if (message.getCreated() == 0L) message.setCreated(System.currentTimeMillis());
        message.setAckRetryCount(0);
        message.setAckLastSent(System.currentTimeMillis());
        localCache.put(ackKey, message);
        log.info("[ACK-LOCAL] 消息入队, msgId {}, sessionId {}, receiver {}",
            message.getId(), sessionId, message.getReceiver());
    }

    private boolean ackFromLocalCache(String receiver, String messageId, String sessionId) {
        String ackKey = buildAckKey(messageId, sessionId);
        NotifyMessage msg = localCache.get(ackKey);
        if (msg == null) {
            return false;
        }
        if (!receiver.equals(msg.getReceiver())) {
            return false;
        }
        localCache.remove(ackKey);
        log.info("[ACK-LOCAL] 确认成功, msgId {}, sessionId {}, retryCount {}",
            messageId, sessionId, msg.getAckRetryCount());
        return true;
    }

    @Scheduled(fixedDelay = ACK_RETRY_INTERVAL_MS)
    public void retryLocalCache() {
        if (!enableLocalAck) return;
        if (localCache.isEmpty()) return;

        int retry = 0, expired = 0, notOnline = 0;
        for (val entry : localCache.entrySet()) {
            val ackKey = entry.getKey();
            val msg = entry.getValue();
            if (msg == null || System.currentTimeMillis() - msg.getCreated() < ACK_CHECK_WAIT_MS) continue;

            String[] parsed = parseAckKey(ackKey);
            String messageId = parsed[0];
            String sessionId = parsed[1];

            if (msg.getAckRetryCount() >= ACK_MAX_RETRY_COUNT ||
                System.currentTimeMillis() - msg.getCreated() > ACK_MESSAGE_TTL_MS) {
                localCache.remove(ackKey);
                expired++;
                log.warn("[ACK-LOCAL] 消息过期/超限, 移除, msgId {}, sessionId {}",
                    parsed[0], parsed[1]);
                continue;
            }

            if (hasSession(msg.getReceiver())) {
                SimpUser user = userRegistry.getUser(msg.getReceiver());
                boolean sessionExists = user.getSessions().stream()
                    .anyMatch(s -> s.getId().equals(sessionId));

                if (sessionExists) {
                    sendMessage(msg, sessionId);
                    msg.setAckRetryCount(msg.getAckRetryCount() + 1);
                    msg.setAckLastSent(System.currentTimeMillis());
                    retry++;
                    log.debug("[ACK-LOCAL] 重发, msgId {}, sessionId {}, retryCount {}",
                        messageId, sessionId, msg.getAckRetryCount());
                    continue;
                }
            }

            notOnline++;
        }

        log.info("[ACK-LOCAL] 定时处理完成, total {}, retried {}, expired {}, not online {}",
            localCache.size(), retry, expired, notOnline);
    }

    // ==================== Redis 实现 ====================

    private void addToRedis(NotifyMessage message, String sessionId) {
        String ackKey = buildAckKey(message.getId(), sessionId);
        RMap<String, NotifyMessage> map = redisson.getMap(PENDING_MAP_KEY);
        if (map.containsKey(ackKey)) {
            log.debug("[ACK-REDIS] 消息已存在, msgId {}, sessionId {}", message.getId(), sessionId);
            return;
        }
        if (message.getCreated() == 0L) message.setCreated(System.currentTimeMillis());
        message.setAckRetryCount(0);
        message.setAckLastSent(System.currentTimeMillis());
        map.put(ackKey, message);
        log.info("[ACK-REDIS] 消息入队, msgId {}, sessionId {}, receiver {}",
            message.getId(), sessionId, message.getReceiver());
    }

    private boolean ackFromRedis(String receiver, String messageId, String sessionId) {
        String ackKey = buildAckKey(messageId, sessionId);
        RMap<String, NotifyMessage> map = redisson.getMap(PENDING_MAP_KEY);
        NotifyMessage msg = map.get(ackKey);
        if (msg == null) {
            return false;
        }
        if (!receiver.equals(msg.getReceiver())) {
            return false;
        }
        map.remove(ackKey);
        log.info("[ACK-REDIS] 确认成功, msgId {}, sessionId {}, retryCount {}",
            messageId, sessionId, msg.getAckRetryCount());
        return true;
    }

    @Scheduled(fixedDelay = ACK_RETRY_INTERVAL_MS)
    public void retryRedisMessages() {
        if (enableLocalAck) return;

        RMap<String, NotifyMessage> map = redisson.getMap(PENDING_MAP_KEY);
        if (map.isEmpty()) return;

        log.info("[ACK-REDIS] 定时处理开始, total {}", map.size());

        int retry = 0, expired = 0, notOnline = 0;
        for (val entry : map.entrySet()) {
            val ackKey = entry.getKey();
            val msg = entry.getValue();
            if (msg == null || System.currentTimeMillis() - msg.getCreated() < ACK_CHECK_WAIT_MS) continue;

            if (msg.getAckRetryCount() >= ACK_MAX_RETRY_COUNT ||
                System.currentTimeMillis() - msg.getCreated() > ACK_MESSAGE_TTL_MS) {
                map.remove(ackKey);
                expired++;
                String[] parsed = parseAckKey(ackKey);
                log.warn("[ACK-REDIS] 消息过期/超限, 移除, msgId {}, sessionId {}",
                    parsed[0], parsed[1]);
                continue;
            }

            if (hasSession(msg.getReceiver())) {
                String[] parsed = parseAckKey(ackKey);
                String messageId = parsed[0];
                String sessionId = parsed[1];

                SimpUser user = userRegistry.getUser(msg.getReceiver());
                boolean sessionExists = user.getSessions().stream()
                    .anyMatch(s -> s.getId().equals(sessionId));

                if (sessionExists) {
                    sendMessage(msg, sessionId);
                    msg.setAckRetryCount(msg.getAckRetryCount() + 1);
                    msg.setAckLastSent(System.currentTimeMillis());
                    map.put(ackKey, msg);
                    retry++;
                    log.debug("[ACK-REDIS] 重发, msgId: {}, sessionId: {}, receiver: {}, retryCount: {}",
                        messageId, sessionId, msg.getReceiver(), msg.getAckRetryCount());
                    continue;
                }
            }

            notOnline++;
        }

        log.info("[ACK-REDIS] 定时处理完成, total {}, retried {}, expired {}, not online {}",
            map.size(), retry, expired, notOnline);
    }
}
