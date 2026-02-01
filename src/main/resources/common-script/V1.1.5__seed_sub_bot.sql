INSERT INTO tg_manager_bot.bot (type, bot_token, description)
SELECT 'SUB', NULL, 'sub bot'
WHERE NOT EXISTS (
    SELECT 1 FROM tg_manager_bot.bot WHERE type = 'SUB'
);
