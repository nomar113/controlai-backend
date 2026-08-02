-- Add ON DELETE CASCADE to the FK so tokens are removed automatically when a user is deleted.
-- The implicit backing index on user_id already exists (created with the FK in V32).
-- Split into two statements because MySQL does not always allow combining DROP + ADD FK in one ALTER.

ALTER TABLE password_reset_tokens DROP FOREIGN KEY fk_prt_user;

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
