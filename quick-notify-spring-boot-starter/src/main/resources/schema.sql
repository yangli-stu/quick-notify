-- 消息通知表
CREATE TABLE IF NOT EXISTS notify_log (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    receiver VARCHAR(64) NOT NULL,
    data TEXT,
    viewed BOOLEAN DEFAULT FALSE,
    created BIGINT,
    last_modified BIGINT,
    version BIGINT DEFAULT 0,
    INDEX idx_receiver_viewed_created (receiver, viewed, created)
);
