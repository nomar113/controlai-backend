-- External identities linked to a user (Google today, Apple next). One row per provider account.
-- provider + subject identifies the external account; user_id + provider allows at most one identity per provider per user.
-- apple_refresh_token_enc stays NULL until the Apple login stores its encrypted refresh token (APPLE rows only).
-- `users.google_sub` is kept in this release and dropped by a later migration.

CREATE TABLE IF NOT EXISTS user_identities (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  provider VARCHAR(20) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  apple_refresh_token_enc VARCHAR(512) DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_identities_provider_subject UNIQUE (provider, subject),
  CONSTRAINT uk_user_identities_user_provider UNIQUE (user_id, provider),
  CONSTRAINT fk_user_identities_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Backfill: every user that already has a google_sub gets a GOOGLE identity.
-- NOT EXISTS keeps the statement idempotent: running it again inserts nothing.
INSERT INTO user_identities (user_id, provider, subject)
SELECT u.id, 'GOOGLE', u.google_sub
FROM users u
WHERE u.google_sub IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM user_identities ui
    WHERE ui.provider = 'GOOGLE' AND ui.subject = u.google_sub
  );
