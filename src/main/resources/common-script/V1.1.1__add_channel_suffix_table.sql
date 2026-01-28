CREATE TABLE IF NOT EXISTS tg_manager_bot.channel_suffix
(
    id                   SERIAL PRIMARY KEY,
    forward_from_chat_id VARCHAR(64) NOT NULL,
    suffix_text          TEXT NOT NULL,
    enabled              BOOLEAN DEFAULT TRUE NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_channel_suffix_forward_from_chat_id
    ON tg_manager_bot.channel_suffix (forward_from_chat_id);
