-- One row per Apple login started by the web or Android app. The row lives from /auth/apple/start to the
-- moment the app exchanges the one-time handoff code. Raw nonce and codes are never stored: only the
-- SHA-256 digests of the nonce (lookup key, matched against the id_token claim) and of the handoff code.

CREATE TABLE IF NOT EXISTS apple_login_flows (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nonce VARCHAR(64) NOT NULL,
  nonce_digest CHAR(64) NOT NULL,
  platform VARCHAR(10) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL,
  handoff_code_hash CHAR(64) NULL,
  user_id BIGINT NULL,
  handoff_expires_at TIMESTAMP NULL,
  handoff_consumed_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_apple_login_flows_nonce_digest UNIQUE (nonce_digest),
  CONSTRAINT uk_apple_login_flows_handoff_code_hash UNIQUE (handoff_code_hash),
  CONSTRAINT fk_apple_login_flows_user FOREIGN KEY (user_id) REFERENCES users(id)
);
