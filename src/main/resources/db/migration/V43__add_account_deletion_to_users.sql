-- Account deletion with a 30-day grace period: the request is a scheduled state on the user
-- itself. The purge job looks up due accounts by deletion_scheduled_for.

ALTER TABLE users
  ADD COLUMN deletion_requested_at  TIMESTAMP NULL DEFAULT NULL,
  ADD COLUMN deletion_scheduled_for TIMESTAMP NULL DEFAULT NULL,
  ADD INDEX idx_users_deletion_scheduled_for (deletion_scheduled_for);
