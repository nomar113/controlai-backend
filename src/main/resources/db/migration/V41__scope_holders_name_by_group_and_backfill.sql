-- Holders were never given a default for new groups, so accounts created after multi-tenancy
-- had no holder and could not register any card (the "Titular" select came up empty).

-- Replace the legacy global unique name constraint (V5, auto-named `name`) with a per-group
-- one, so two groups can have a holder with the same name.
ALTER TABLE holders
    DROP INDEX `name`,
    ADD CONSTRAINT uk_holders_group_name UNIQUE (group_id, name);

-- Backfill: every group without a holder gets one named after the group (the owner's name).
INSERT INTO holders (group_id, name)
SELECT g.id, LEFT(g.name, 100)
FROM `groups` g
WHERE NOT EXISTS (SELECT 1 FROM holders h WHERE h.group_id = g.id);
