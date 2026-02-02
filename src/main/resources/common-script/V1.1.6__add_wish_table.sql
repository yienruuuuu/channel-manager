CREATE TABLE IF NOT EXISTS tg_manager_bot.wish
(
    id          VARCHAR(36) PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    platform    VARCHAR(128) NOT NULL,
    person_name VARCHAR(128) NOT NULL,
    wish_type   VARCHAR(128) NOT NULL,
    other_text  VARCHAR(100) NOT NULL,
    ins_dat     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wish_user_ins_dat
    ON tg_manager_bot.wish (user_id, ins_dat);

COMMENT ON COLUMN tg_manager_bot.wish.id IS '主鍵 UUID';
COMMENT ON COLUMN tg_manager_bot.wish.user_id IS 'Telegram 使用者 ID';
COMMENT ON COLUMN tg_manager_bot.wish.platform IS '許願平台';
COMMENT ON COLUMN tg_manager_bot.wish.person_name IS '許願人名';
COMMENT ON COLUMN tg_manager_bot.wish.wish_type IS '許願類型';
COMMENT ON COLUMN tg_manager_bot.wish.other_text IS '許願其他(限制 100 字)';
COMMENT ON COLUMN tg_manager_bot.wish.ins_dat IS '許願建立時間';
COMMENT ON COLUMN tg_manager_bot.wish.created_at IS '資料建立時間';
COMMENT ON COLUMN tg_manager_bot.wish.updated_at IS '資料更新時間';
