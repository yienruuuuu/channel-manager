ALTER TABLE tg_manager_bot.forward_post
    ADD COLUMN IF NOT EXISTS forward_from_user_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS forward_from_user_username VARCHAR(255),
    ADD COLUMN IF NOT EXISTS forward_from_user_name VARCHAR(255);
