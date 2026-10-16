-- The state the app sends to Apple, checked on the callback and echoed back to the app on the redirect.
-- Rows created before this migration have no state and can no longer complete a callback.

ALTER TABLE apple_login_flows
  ADD COLUMN state CHAR(64) NULL;
