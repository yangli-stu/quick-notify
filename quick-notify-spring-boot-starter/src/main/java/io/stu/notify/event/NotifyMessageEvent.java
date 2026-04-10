package io.stu.notify.event;

import io.stu.notify.repository.NotifyMessageLog;

/**
 * 消息事件，用于 Spring 事件发布
 */
public record NotifyMessageEvent(NotifyMessageLog notifyMessageLog) {
}
