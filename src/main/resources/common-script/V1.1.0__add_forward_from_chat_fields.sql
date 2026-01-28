ALTER TABLE tg_manager_bot.forward_post
    ADD COLUMN IF NOT EXISTS forward_from_chat_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS forward_from_chat_title VARCHAR(255);
