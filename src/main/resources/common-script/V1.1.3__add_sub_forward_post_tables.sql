CREATE TABLE IF NOT EXISTS tg_manager_bot.sub_forward_post
(
    id                    VARCHAR(36) PRIMARY KEY,
    serial                VARCHAR(32) NOT NULL,
    source_chat_id        VARCHAR(64),
    source_message_id     INTEGER,
    source_media_group_id VARCHAR(64),
    forward_from_chat_id  VARCHAR(64),
    forward_from_chat_title VARCHAR(255),
    forward_from_user_id VARCHAR(64),
    forward_from_user_username VARCHAR(255),
    forward_from_user_name VARCHAR(255),
    original_text         TEXT,
    processed_text        TEXT,
    output_text           TEXT,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tg_manager_bot.sub_forward_post_media
(
    id         SERIAL PRIMARY KEY,
    post_id    VARCHAR(36) NOT NULL,
    media_type VARCHAR(32) NOT NULL,
    file_id    VARCHAR(512) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sub_forward_post_media_post_id
    ON tg_manager_bot.sub_forward_post_media (post_id);

ALTER TABLE tg_manager_bot.sub_forward_post_media
    ADD CONSTRAINT fk_sub_forward_post_media_post
        FOREIGN KEY (post_id) REFERENCES tg_manager_bot.sub_forward_post (id)
            ON DELETE CASCADE;
