-- Password reset tokens for the forgot-password / reset-password flow.
-- Token is stored as a SHA-256 hex hash; the raw value is emailed to the user.
-- Tokens are single-use (used_at is set on redemption) and expire after 1 hour.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_prt_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id)
);
