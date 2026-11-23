-- Run against Railway PostgreSQL BEFORE deploying the new code.
-- Existing rows remain valid; this column is deliberately nullable.
BEGIN;
ALTER TABLE sale ADD COLUMN IF NOT EXISTS recorded_by VARCHAR(100);
COMMIT;
