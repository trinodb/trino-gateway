-- Align `exact_match_source_selectors` table

-- Shrink `environment` column length
ALTER TABLE exact_match_source_selectors MODIFY environment VARCHAR(128);


-- Align `resource_groups` table

-- Make `soft_memory_limit` nullable
ALTER TABLE resource_groups MODIFY soft_memory_limit VARCHAR(128) NULL;


-- Align `selectors` table

-- Add missing columns
ALTER TABLE selectors ADD (
  user_group_regex VARCHAR2(2048),
  original_user_regex VARCHAR2(512),
  authenticated_user_regex VARCHAR2(512),
  id NUMBER GENERATED ALWAYS as IDENTITY(START with 1 INCREMENT by 1) PRIMARY KEY
);
