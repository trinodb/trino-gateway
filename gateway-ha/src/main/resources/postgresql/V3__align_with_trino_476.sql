-- Align `exact_match_source_selectors` table

-- Adjust primary key
ALTER TABLE exact_match_source_selectors
    DROP CONSTRAINT IF EXISTS exact_match_source_selectors_pkey;

ALTER TABLE exact_match_source_selectors
    ADD PRIMARY KEY (environment, source, resource_group_id);

-- Extend the length of `query_type`
ALTER TABLE exact_match_source_selectors
    ALTER COLUMN query_type TYPE VARCHAR(512);


-- Align resource_groups table

-- Drop the unique constraint on `name` if it exists
ALTER TABLE resource_groups DROP CONSTRAINT IF EXISTS resource_groups_name_key;

-- Alter `resource_group_id` from integer to bigint
ALTER TABLE resource_groups ALTER COLUMN resource_group_id TYPE bigint;

-- Make `soft_memory_limit` nullable
ALTER TABLE resource_groups ALTER COLUMN soft_memory_limit DROP NOT NULL;

-- Drop and recreate the `parent` foreign key with ON DELETE CASCADE
ALTER TABLE resource_groups DROP CONSTRAINT IF EXISTS resource_groups_parent_fkey;

ALTER TABLE resource_groups
  ADD CONSTRAINT resource_groups_parent_fkey
  FOREIGN KEY (parent) REFERENCES resource_groups(resource_group_id) ON DELETE CASCADE;


-- Align `selectors` table

-- Add missing columns
ALTER TABLE selectors
    ADD COLUMN user_group_regex VARCHAR(2048),
    ADD COLUMN original_user_regex VARCHAR(512),
    ADD COLUMN authenticated_user_regex VARCHAR(512),
    ADD COLUMN id BIGSERIAL PRIMARY KEY;

-- Drop and recreate the `resource_group_id` foreign key with ON DELETE CASCADE
ALTER TABLE selectors DROP CONSTRAINT IF EXISTS selectors_resource_group_id_fkey;

ALTER TABLE selectors
  ADD CONSTRAINT selectors_resource_group_id_fkey
  FOREIGN KEY (resource_group_id)
  REFERENCES resource_groups(resource_group_id)
  ON DELETE CASCADE;
