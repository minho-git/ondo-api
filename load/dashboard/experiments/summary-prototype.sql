-- 사전 집계 요약 테이블 (프로토타입)
--
-- 대시보드가 매번 세던 것을 미리 세어 둔다. 여기서는 기존 데이터로 한 번 채우고(backfill)
-- 읽기 쪽 효과만 잰다. 제품 코드에 넣는 건 수치를 보고 결정한다.
--
--   docker exec -i perf-pg psql -U ondo -d ondo_wholesale -q < summary.sql
--
-- 영업일 경계는 KST 낮 12시다 — 12시 이전 주문은 전날 영업일에 속한다.

SET search_path TO wholesale;

DROP TABLE IF EXISTS dashboard_daily, dashboard_backorder_sku, dashboard_packing_queue;

-- ── 표 1. 영업일별 주문 요약 ────────────────────────────────
CREATE TABLE dashboard_daily (
    wholesaler_id   bigint NOT NULL,
    business_day    date   NOT NULL,
    order_count     int    NOT NULL DEFAULT 0,
    order_amount    bigint NOT NULL DEFAULT 0,
    cancelled_count int    NOT NULL DEFAULT 0,
    PRIMARY KEY (wholesaler_id, business_day)
);

INSERT INTO dashboard_daily (wholesaler_id, business_day, order_count, order_amount, cancelled_count)
SELECT o.wholesaler_id,
       ((o.ordered_at AT TIME ZONE 'Asia/Seoul') - interval '12 hour')::date,
       count(DISTINCT o.id),
       coalesce(sum(oi.qty * oi.unit_price), 0),
       count(DISTINCT o.id) FILTER (WHERE o.status = 'CANCELLED')
FROM orders o
LEFT JOIN order_item oi ON oi.order_id = o.id
GROUP BY 1, 2;

-- ── 표 2. 미송 SKU 요약 ─────────────────────────────────────
-- 입고일 지남·미등록은 날짜가 바뀌면 저절로 변하므로 세어두지 않는다.
-- SKU 단위 잔여량만 들고, 날짜 판정은 읽을 때 한다.
CREATE TABLE dashboard_backorder_sku (
    wholesaler_id     bigint NOT NULL,
    variant_id        bigint NOT NULL,
    open_qty          int    NOT NULL DEFAULT 0,
    oldest_created_at timestamptz,
    PRIMARY KEY (wholesaler_id, variant_id)
);

INSERT INTO dashboard_backorder_sku (wholesaler_id, variant_id, open_qty, oldest_created_at)
SELECT o.wholesaler_id, oi.variant_id,
       sum(oi.qty - oi.allocated_qty),
       min(b.created_at)
FROM backorder b
JOIN order_item oi ON oi.id = b.order_item_id
JOIN orders o      ON o.id = oi.order_id
WHERE b.status = 'OPEN'
GROUP BY 1, 2;

-- ── 표 3. 포장 대기 요약 ────────────────────────────────────
-- 소매처 수를 세지 않고 소매처를 행으로 둔다 — 세는 대신 행 수를 읽는다.
CREATE TABLE dashboard_packing_queue (
    wholesaler_id  bigint      NOT NULL,
    retailer_id    bigint      NOT NULL,
    receive_method varchar(20) NOT NULL,
    qty            int         NOT NULL DEFAULT 0,
    PRIMARY KEY (wholesaler_id, retailer_id, receive_method)
);

INSERT INTO dashboard_packing_queue (wholesaler_id, retailer_id, receive_method, qty)
SELECT o.wholesaler_id, pt.retailer_id, o.receive_method, sum(pi.qty)
FROM packing_item pi
JOIN packing pk ON pk.id = pi.packing_id
JOIN orders o   ON o.id = pk.order_id
JOIN partner pt ON pt.id = o.partner_id
WHERE pk.status = 'READY' AND pk.outbound_id IS NULL AND pi.deleted_at IS NULL
GROUP BY 1, 2, 3;

ANALYZE;

-- 채워진 규모
SELECT 'dashboard_daily'       AS t, count(*) FROM dashboard_daily
UNION ALL SELECT 'dashboard_backorder_sku', count(*) FROM dashboard_backorder_sku
UNION ALL SELECT 'dashboard_packing_queue', count(*) FROM dashboard_packing_queue;
