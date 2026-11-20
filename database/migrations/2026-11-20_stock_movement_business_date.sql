-- SmartStock: Stock movement business date
-- Preserves the original recorded_at timestamp.
BEGIN;
ALTER TABLE public.stock_movement ADD COLUMN IF NOT EXISTS business_date DATE;
COMMIT;
