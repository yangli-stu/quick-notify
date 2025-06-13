package io.stu.notify.event;

import io.stu.notify.model.NotifyMessageLog;

public record NotifyMessageEvent(NotifyMessageLog notifyMessageLog) {
}