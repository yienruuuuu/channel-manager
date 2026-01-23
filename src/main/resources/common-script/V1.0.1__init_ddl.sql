CREATE SCHEMA IF NOT EXISTS tg_draw_bot;

DROP TABLE IF EXISTS tg_draw_bot.bot;
CREATE TABLE tg_draw_bot.bot
(
    id                     SERIAL PRIMARY KEY,
    type                   VARCHAR(50) NOT NULL DEFAULT 'MAIN',
    bot_token              VARCHAR(512),
    description            VARCHAR(512),
    bot_telegram_user_name VARCHAR(50), -- 機器人的telegram用戶名
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);






