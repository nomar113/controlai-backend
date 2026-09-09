-- Billing foundation: subscriptions per group (tenancy unit) and a Kiwify webhook event log
-- used for idempotency and auditing.
-- Note: `groups` is a reserved word in MySQL 8, so it must always be backtick-quoted.

CREATE TABLE IF NOT EXISTS subscriptions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id BIGINT NOT NULL,
  plan ENUM('ANNUAL', 'LIFETIME', 'GRANDFATHERED') NOT NULL,
  status ENUM('ACTIVE', 'CANCELLED', 'EXPIRED') NOT NULL,
  kiwify_order_id VARCHAR(255) DEFAULT NULL,
  current_period_end TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_subscriptions_group_id UNIQUE (group_id),
  CONSTRAINT fk_subscriptions_group FOREIGN KEY (group_id) REFERENCES `groups`(id)
);

CREATE TABLE IF NOT EXISTS kiwify_webhook_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  kiwify_event_id VARCHAR(255) NOT NULL,
  order_status VARCHAR(50) NOT NULL,
  raw_payload JSON NOT NULL,
  processed_at TIMESTAMP NULL DEFAULT NULL,
  CONSTRAINT uk_kiwify_webhook_events_event_id UNIQUE (kiwify_event_id)
);

-- Grandfathering: every group that existed before the commercial launch keeps free lifetime access.
INSERT INTO subscriptions (group_id, plan, status)
SELECT id, 'GRANDFATHERED', 'ACTIVE' FROM `groups`;
