-- Apply to PostgreSQL BEFORE deploying the automated-reference Java update.
-- Existing delivery references are preserved. Old records get NULL request IDs.
BEGIN;
ALTER TABLE public.sale ADD COLUMN IF NOT EXISTS delivery_request_id VARCHAR(36);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sale_delivery_request_id ON public.sale (delivery_request_id);
COMMIT;
