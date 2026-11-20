-- Apply to local PostgreSQL first; apply to Railway BEFORE deploying new Java.
-- No old customer accounts or timestamps are overwritten.
BEGIN;
ALTER TABLE public.customer ADD COLUMN IF NOT EXISTS customer_type VARCHAR(24) NOT NULL DEFAULT 'RETAIL';
ALTER TABLE public.sale ADD COLUMN IF NOT EXISTS business_date DATE;
ALTER TABLE public.sale ADD COLUMN IF NOT EXISTS delivery_reference VARCHAR(80);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sale_delivery_reference ON public.sale (delivery_reference);
ALTER TABLE public.credit_payment ADD COLUMN IF NOT EXISTS business_date DATE;
COMMIT;
