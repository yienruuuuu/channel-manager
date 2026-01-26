CREATE TABLE IF NOT EXISTS tg_manager_bot.blacklist_term
(
    id         SERIAL PRIMARY KEY,
    term       VARCHAR(512) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_blacklist_term_term
    ON tg_manager_bot.blacklist_term (term);
