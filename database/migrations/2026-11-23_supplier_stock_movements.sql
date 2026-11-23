-- Run on production PostgreSQL AFTER verifying the backup and BEFORE deploying this code.
-- Historical stock movements keep NULL supplier and receipt; nothing is backfilled or deleted.
BEGIN;
ALTER TABLE stock_movement ADD COLUMN IF NOT EXISTS supplier_id BIGINT;
ALTER TABLE stock_movement ADD COLUMN IF NOT EXISTS delivery_receipt_number VARCHAR(80);
ALTER TABLE stock_movement ADD COLUMN IF NOT EXISTS supplier_name_at_delivery VARCHAR(150);
ALTER TABLE stock_movement ADD COLUMN IF NOT EXISTS supplier_code_at_delivery VARCHAR(40);
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname='fk_stock_movement_supplier'
          AND conrelid='public.stock_movement'::regclass
    ) THEN
        ALTER TABLE public.stock_movement
          ADD CONSTRAINT fk_stock_movement_supplier
          FOREIGN KEY (supplier_id) REFERENCES public.supplier(id) ON DELETE RESTRICT;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_stock_movement_supplier_id
  ON public.stock_movement(supplier_id);
COMMIT;
