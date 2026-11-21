-- OPTIONAL LOCAL TEST DATA. DO NOT RUN AGAINST RAILWAY PRODUCTION.
-- Reconstructs the user's November 15 & 16 Aljun debt list and Whole Chicken.
-- Only touches historical_day, historical_debt, historical_stock evidence tables.
-- Will NOT post stock, sale, debt charges or payments to operational tables.
-- Requires code ALJ to be present in stock_location.
BEGIN;

INSERT INTO historical_day
 (location_id, business_date, collections, source_file, notes, debts_complete, stock_complete,
  status, recorded_by, recorded_at)
SELECT l.id, v.day, v.collections, v.source_file, v.notes, true, false, 'DRAFT', 'workbook-reference', now()
FROM stock_location l CROSS JOIN (
  VALUES
  ('2026-11-15'::date, 80650.00::numeric, 'November 15, 2026.xlsx'::varchar,
    'Excel debt list complete; prior-day debt unavailable; stock list incomplete.'::varchar),
  ('2026-11-16'::date, 79564.00::numeric, 'November 16, 2026.xlsx'::varchar,
    'Excel debt list complete; source includes other suppliers and frozen stock; stock list incomplete.'::varchar)
) AS v(day, collections, source_file, notes)
WHERE l.code = 'ALJ'
ON CONFLICT (location_id, business_date) DO NOTHING;

INSERT INTO historical_debt(day_id, customer_name, customer_key, closing_balance)
SELECT d.id, v.customer, upper(v.customer), v.amount
FROM historical_day d JOIN stock_location l ON l.id = d.location_id
JOIN (
 VALUES
 ('2026-11-15'::date, 'DEPED', 30000.00::numeric),
 ('2026-11-15'::date, 'PASIL', 12780.00::numeric),
 ('2026-11-15'::date, 'CORDOVA', 12771.00::numeric),
 ('2026-11-15'::date, 'RAMOS', 2270.00::numeric),
 ('2026-11-15'::date, 'NERISA', 2497.00::numeric),
 ('2026-11-15'::date, 'LISA', 1354.00::numeric),
 ('2026-11-16'::date, 'DEPED', 30000.00::numeric),
 ('2026-11-16'::date, 'PASIL', 12780.00::numeric),
 ('2026-11-16'::date, 'CORDOVA', 11286.00::numeric),
 ('2026-11-16'::date, 'RAMOS', 3080.00::numeric),
 ('2026-11-16'::date, 'NERISA', 2197.00::numeric),
 ('2026-11-16'::date, 'LISA', 1643.00::numeric)
) AS v(day, customer, amount) ON v.day = d.business_date
WHERE l.code = 'ALJ' AND d.status = 'DRAFT'
ON CONFLICT (day_id, customer_key) DO NOTHING;

INSERT INTO historical_stock(day_id, product_name, product_key, unit, available_qty, closing_qty,
                             known_loss_qty, selling_price)
SELECT d.id, 'Whole chicken', 'WHOLE CHICKEN:KG', 'KG', v.available, v.closing, 0, 175.00
FROM historical_day d JOIN stock_location l ON l.id = d.location_id
JOIN (
 VALUES
 ('2026-11-15'::date, 244.390::numeric, 52.200::numeric),
 ('2026-11-16'::date, 219.850::numeric, 0.000::numeric)
) AS v(day, available, closing) ON v.day = d.business_date
WHERE l.code = 'ALJ' AND d.status = 'DRAFT'
ON CONFLICT (day_id, product_key) DO NOTHING;

COMMIT;
