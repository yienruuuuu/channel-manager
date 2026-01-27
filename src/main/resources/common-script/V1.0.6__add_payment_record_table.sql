CREATE TABLE IF NOT EXISTS tg_manager_bot.payment_record
(
    id                         VARCHAR(36) PRIMARY KEY,
    user_id                    BIGINT NOT NULL,
    chat_id                    BIGINT NOT NULL,
    message_id                 INTEGER,
    invoice_payload            VARCHAR(256) NOT NULL,
    currency                   VARCHAR(8) NOT NULL,
    total_amount               INTEGER NOT NULL,
    telegram_payment_charge_id VARCHAR(128) NOT NULL,
    provider_payment_charge_id VARCHAR(128),
    status                     VARCHAR(32) NOT NULL DEFAULT 'PAID',
    refunded_at                TIMESTAMP,
    created_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_record_telegram_charge
    ON tg_manager_bot.payment_record (telegram_payment_charge_id);

CREATE INDEX IF NOT EXISTS idx_payment_record_user
    ON tg_manager_bot.payment_record (user_id);

CREATE INDEX IF NOT EXISTS idx_payment_record_payload
    ON tg_manager_bot.payment_record (invoice_payload);
