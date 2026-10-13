-- =============================================================================
-- TimeCapsule & Legacy Messaging — schema upgrade (PostgreSQL)
-- =============================================================================
-- This script is idempotent-ish for a fresh install and safe to re-run because
-- every ALTER is guarded. It complements Hibernate's `ddl-auto` for teams that
-- prefer explicit, reviewable DDL (e.g. production / Supabase). A Liquibase
-- equivalent lives in db/changelog/001-timecapsule.yaml.
--
-- Run order matters: reactions/subscriptions reference existing tables.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. messages — rich media + time-lock columns
-- -----------------------------------------------------------------------------
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS message_type VARCHAR(16)  NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS media_url    VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS is_locked    BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS unlock_at    TIMESTAMP WITH TIME ZONE;

-- content becomes optional (media-only or capsule messages may carry no text).
ALTER TABLE messages
    ALTER COLUMN content DROP NOT NULL;

-- Partial index to quickly find still-sealed capsules.
CREATE INDEX IF NOT EXISTS idx_messages_locked_unlock
    ON messages (is_locked, unlock_at)
    WHERE is_locked = TRUE;

-- Index media lookups by conversation (history query already orders by createdAt).
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 2. reactions — emoji reactions, one row per (message, user, type)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reactions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id    BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    reaction_type VARCHAR(16) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reactions_message FOREIGN KEY (message_id)
        REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_reactions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- Prevent a user spamming the same reaction on the same message.
    CONSTRAINT uq_reaction_message_user_type
        UNIQUE (message_id, user_id, reaction_type)
);

CREATE INDEX IF NOT EXISTS idx_reactions_message ON reactions (message_id);
CREATE INDEX IF NOT EXISTS idx_reactions_user    ON reactions (user_id);

-- -----------------------------------------------------------------------------
-- 3. subscriptions — tier entitlements for users OR groups (polymorphic entity_id)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscriptions (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_id  BIGINT      NOT NULL,               -- users.id OR conversations.id
    tier_type  VARCHAR(24) NOT NULL,               -- FREE_PERSONAL | PAID_FAMILY
    expires_at TIMESTAMP WITH TIME ZONE,           -- NULL = never expires
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_tier
        CHECK (tier_type IN ('FREE_PERSONAL', 'PAID_FAMILY'))
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_entity ON subscriptions (entity_id);

COMMIT;

-- -----------------------------------------------------------------------------
-- Verification queries
-- -----------------------------------------------------------------------------
-- \d messages
-- \d reactions
-- \d subscriptions
-- SELECT message_type, COUNT(*) FROM messages GROUP BY message_type;
