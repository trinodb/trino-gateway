-- Align `exact_match_source_selectors` table

-- Adjust primary key
ALTER TABLE exact_match_source_selectors
  DROP PRIMARY KEY;

ALTER TABLE exact_match_source_selectors
  ADD PRIMARY KEY (environment, source(128), resource_group_id);

-- Modify `query_type` to be nullable
ALTER TABLE exact_match_source_selectors
  MODIFY COLUMN query_type VARCHAR(512) DEFAULT NULL;


-- Align `resource_groups` table

-- Drop the unique constraint on `name`
ALTER TABLE resource_groups
  DROP INDEX name;

-- Make `soft_memory_limit` nullable
ALTER TABLE resource_groups
  MODIFY COLUMN soft_memory_limit VARCHAR(128) DEFAULT NULL;

-- Drop and recreate the `parent` foreign key with ON DELETE CASCADE
ALTER TABLE resource_groups
  DROP FOREIGN KEY resource_groups_ibfk_1;

ALTER TABLE resource_groups
  ADD CONSTRAINT resource_groups_ibfk_1
  FOREIGN KEY (parent) REFERENCES resource_groups(resource_group_id)
  ON DELETE CASCADE;


-- Align `selectors` table

-- Add missing columns
ALTER TABLE selectors
  ADD COLUMN user_group_regex VARCHAR(2048) DEFAULT NULL,
  ADD COLUMN original_user_regex VARCHAR(512) DEFAULT NULL,
  ADD COLUMN authenticated_user_regex VARCHAR(512) DEFAULT NULL,
  ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY;

-- Drop and recreate the `resource_group_id` foreign key with ON DELETE CASCADE
ALTER TABLE selectors DROP FOREIGN KEY selectors_ibfk_1;

ALTER TABLE selectors
  ADD CONSTRAINT selectors_ibfk_1
  FOREIGN KEY (resource_group_id)
  REFERENCES resource_groups(resource_group_id)
  ON DELETE CASCADE;
