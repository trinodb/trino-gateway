ALTER TABLE query_history ADD (query_text_clob CLOB);
UPDATE query_history SET query_text_clob = query_text;
ALTER TABLE query_history DROP COLUMN query_text;
ALTER TABLE query_history RENAME COLUMN query_text_clob TO query_text;
