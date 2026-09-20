-- 측정용 인덱스만 제거한다 (V1 이 만든 것은 남긴다)
SET search_path TO wholesale;
DROP INDEX IF EXISTS order_item_order_idx;
DROP INDEX IF EXISTS packing_item_packing_idx;
DROP INDEX IF EXISTS orders_new_idx;
DROP INDEX IF EXISTS orders_recent_idx;
DROP INDEX IF EXISTS outbound_open_idx;
DROP INDEX IF EXISTS outbound_shipped_idx;
DROP INDEX IF EXISTS order_item_variant_idx;
ANALYZE;
