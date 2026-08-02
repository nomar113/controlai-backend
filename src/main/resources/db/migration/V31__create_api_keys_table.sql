-- API keys used by the iPhone Shortcut to authenticate POST /payments/notification.
-- One key per group; value is never stored in plain text, only its SHA-256 hex digest.

CREATE TABLE IF NOT EXISTS api_keys (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id    BIGINT       NOT NULL,
  key_hash    CHAR(64)     NOT NULL,
  label       VARCHAR(255) NOT NULL DEFAULT 'iPhone Shortcut',
  created_at  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
  revoked_at  TIMESTAMP    NULL     DEFAULT NULL,
  CONSTRAINT uk_api_keys_hash  UNIQUE (key_hash),
  CONSTRAINT fk_api_keys_group FOREIGN KEY (group_id) REFERENCES `groups`(id)
);
