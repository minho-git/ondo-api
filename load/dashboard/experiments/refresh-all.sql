-- 도매처 3만 곳 요약 전체 재계산 — 앱의 DashboardSummaryRefresher 와 같은 SQL 을 한 번에 돈다.
SET search_path TO wholesale;
\timing on

TRUNCATE dashboard_daily, dashboard_counter, dashboard_backorder_sku, dashboard_packing_queue;

INSERT INTO dashboard_daily (wholesaler_id, business_day, order_count, order_amount, cancelled_count)
SELECT o.wholesaler_id,
       ((o.ordered_at AT TIME ZONE 'Asia/Seoul') - interval '12 hour')::date,
       count(DISTINCT o.id), coalesce(sum(oi.qty * oi.unit_price), 0),
       count(DISTINCT o.id) FILTER (WHERE o.status = 'CANCELLED')
FROM orders o LEFT JOIN order_item oi ON oi.order_id = o.id
GROUP BY 1, 2;

INSERT INTO dashboard_counter (wholesaler_id, new_count)
SELECT wholesaler_id, count(*) FROM orders WHERE status = 'NEW' GROUP BY 1;

INSERT INTO dashboard_backorder_sku (wholesaler_id, variant_id, open_qty, oldest_created_at)
SELECT o.wholesaler_id, oi.variant_id, sum(oi.qty - oi.allocated_qty), min(b.created_at)
FROM backorder b
JOIN order_item oi ON oi.id = b.order_item_id
JOIN orders o      ON o.id = oi.order_id
WHERE b.status = 'OPEN'
GROUP BY 1, 2 HAVING sum(oi.qty - oi.allocated_qty) > 0;

INSERT INTO dashboard_packing_queue (wholesaler_id, retailer_id, receive_method, qty)
SELECT o.wholesaler_id, pt.retailer_id, o.receive_method, sum(pi.qty)
FROM packing_item pi
JOIN packing pk ON pk.id = pi.packing_id
JOIN orders o   ON o.id = pk.order_id
JOIN partner pt ON pt.id = o.partner_id
WHERE pk.status = 'READY' AND pk.outbound_id IS NULL AND pi.deleted_at IS NULL
GROUP BY 1, 2, 3;

ANALYZE;
