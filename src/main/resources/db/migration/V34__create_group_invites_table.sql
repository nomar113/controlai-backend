-- Group sharing invites: one user invites another by email to join their group.
-- A user can belong to exactly one active group (group_members.user_id is unique).

CREATE TABLE IF NOT EXISTS group_invites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id BIGINT NOT NULL,
  inviter_user_id BIGINT NOT NULL,
  invitee_email VARCHAR(255) NOT NULL,
  status ENUM('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
  token CHAR(36) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_group_invites_token UNIQUE (token),
  CONSTRAINT fk_group_invites_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
  CONSTRAINT fk_group_invites_inviter FOREIGN KEY (inviter_user_id) REFERENCES users(id),
  INDEX idx_group_invites_invitee_status (invitee_email, status),
  INDEX idx_group_invites_group_status (group_id, status)
);
