-- 대시보드 집계용 인덱스 (1차)
--
-- 기준선에서 확인한 것: orders · order_item · packing_item 을 통째로 훑고 있다.
-- 조건은 전부 "내 도매처"인데 거기로 바로 가는 길이 없어서다.
--
--   docker exec -i perf-pg psql -U ondo -d ondo_wholesale < index-01.sql

SET search_path TO wholesale;

-- ① 외래키 인덱스 — PostgreSQL 은 FK 에 인덱스를 자동으로 만들지 않는다.
--    미송·오늘주문 집계가 주문 라인 60만 건을 훑던 원인.
CREATE INDEX IF NOT EXISTS order_item_order_idx     ON order_item (order_id);
CREATE INDEX IF NOT EXISTS packing_item_packing_idx ON packing_item (packing_id) WHERE deleted_at IS NULL;

-- ② 주문 — 도매처 + 상태로 거른 뒤 오래된 순으로 뽑는다(신규 주문 카드).
CREATE INDEX IF NOT EXISTS orders_new_idx ON orders (wholesaler_id, ordered_at, id) WHERE status = 'NEW';

-- ③ 주문 — 영업일 안 주문(오늘 주문 카드).
CREATE INDEX IF NOT EXISTS orders_recent_idx ON orders (wholesaler_id, ordered_at);

-- ④ 봉투 — 출고 안 찍은 것만 본다. 부분 인덱스라 크기가 작다.
CREATE INDEX IF NOT EXISTS outbound_open_idx    ON outbound (wholesaler_id, created_at) WHERE shipped_at IS NULL;
CREATE INDEX IF NOT EXISTS outbound_shipped_idx ON outbound (wholesaler_id, shipped_at) WHERE shipped_at IS NOT NULL;

-- ⑤ 미송 조인 경로 — backorder 는 status='OPEN' 부분 인덱스가 이미 있다(V1).
--    반대 방향(주문 라인 → variant)도 타므로 variant 를 거들 인덱스를 둔다.
CREATE INDEX IF NOT EXISTS order_item_variant_idx ON order_item (variant_id);

ANALYZE;
