CREATE TABLE IF NOT EXISTS tg_manager_bot.config
(
    config_type VARCHAR(64) PRIMARY KEY,
    value       VARCHAR(256) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tg_manager_bot.config (config_type, value)
VALUES ('cashier_star_price', '50')
ON CONFLICT (config_type) DO NOTHING;

INSERT INTO tg_manager_bot.bot (bot_token, description, type)
SELECT '', 'CASHIER BOT', 'CASHIER'
WHERE NOT EXISTS (SELECT 1 FROM tg_manager_bot.bot WHERE type = 'CASHIER');
