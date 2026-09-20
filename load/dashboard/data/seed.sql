-- 대시보드 집계 성능 측정용 대량 데이터
--
--   docker exec -i perf-pg psql -U ondo -d ondo_wholesale \
--     -v n_orders=3000000 -v n_wholesaler=500 -v skew=0 -f - < seed.sql
--
-- 규모의 근거 — 동대문 하루 거래액 600억 ÷ 도매 3만 곳 ÷ 건당 10만원 = 도매 한 곳 하루 20건.
-- 연 300일이면 6,000건, 상가 하나(도매 500곳)는 1년에 300만 건.
--   30만 = 상가 1곳 1개월 · 100만 = 4개월 · 300만 = 1년
--
-- skew=0 도매처에 고르게 / skew=1 이면 1번 도매처에 절반 (대형 매장 조건)

\set ON_ERROR_STOP on
\timing off
SET search_path TO wholesale, common, public;

\if :{?n_orders}     \else \set n_orders 200000 \endif
\if :{?n_wholesaler} \else \set n_wholesaler 500 \endif
\if :{?skew}         \else \set skew 0 \endif

\set n_partner_per_w 20
\set n_product_per_w 8

-- ── 도매처 ──────────────────────────────────────────────────
INSERT INTO wholesaler (id, email, password_hash, biz_reg_no, biz_name, biz_owner_name, approval_status, approved_at)
SELECT g, 'perf' || g || '@ondo.test', '(perf)', lpad(g::text, 10, '0'),
       'perf 도매 ' || g, '사장 ' || g, 'APPROVED', now()
FROM generate_series(1, :n_wholesaler) g;

-- ── 거래처 ──────────────────────────────────────────────────
INSERT INTO partner (wholesaler_id, retailer_id, retailer_name)
SELECT w, (w - 1) * :n_partner_per_w + p, 'perf 소매 ' || ((w - 1) * :n_partner_per_w + p)
FROM generate_series(1, :n_wholesaler) w, generate_series(1, :n_partner_per_w) p;

-- ── 상품 · SKU ──────────────────────────────────────────────
INSERT INTO product (wholesaler_id, product_number, name, category_id)
SELECT w, p, 'perf 상품 ' || w || '-' || p,
       (SELECT id FROM common.category WHERE depth = 3 ORDER BY id LIMIT 1)
FROM generate_series(1, :n_wholesaler) w, generate_series(1, :n_product_per_w) p;

INSERT INTO color_option (product_id, color_id)
SELECT pr.id, (SELECT id FROM common.color ORDER BY id LIMIT 1) FROM product pr;

INSERT INTO variant (color_option_id, product_id, size, variant_seq, stock_qty, expected_inbound_date)
SELECT co.id, co.product_id, s.size, s.seq, 100 + (co.id % 50),
       CASE co.id % 3
           WHEN 0 THEN current_date - (((co.id % 5) + 1)::int)
           WHEN 1 THEN current_date + (((co.id % 7) + 1)::int)
           ELSE NULL
       END
FROM color_option co, (VALUES ('S', 1), ('M', 2), ('L', 3)) AS s(size, seq);

-- ── 주문 ────────────────────────────────────────────────────
-- NEW 15% · CONFIRMED 75%(그중 출고 완료분 포함) · CANCELLED 10%
-- 최근 1년에 흩뿌리고 5%는 오늘(영업일 안)
INSERT INTO orders (order_number, retail_order_id, partner_id, wholesaler_id, status,
                    payment_term, receive_method, ordered_at, confirmed_at)
SELECT g, g,
       (w.id - 1) * :n_partner_per_w + (g % :n_partner_per_w) + 1,
       w.id,
       CASE WHEN r.bucket < 3 THEN 'NEW'
            WHEN r.bucket < 18 THEN 'CONFIRMED' ELSE 'CANCELLED' END,
       CASE WHEN g % 2 = 0 THEN 'CASH' ELSE 'BANK_TRANSFER' END,
       CASE WHEN g % 3 = 0 THEN 'AGENT' ELSE 'RETAILER' END,
       CASE WHEN r.day = 0 THEN now() - ((g % 6) || ' hour')::interval
            ELSE now() - ((r.day) || ' day')::interval END,
       CASE WHEN r.bucket < 3 THEN NULL
            ELSE now() - ((r.day) || ' day')::interval END
FROM generate_series(1, :n_orders) g
CROSS JOIN LATERAL (
    SELECT CASE WHEN :skew = 1 AND g % 2 = 0 THEN 1 ELSE (g % :n_wholesaler) + 1 END AS id
) w
CROSS JOIN LATERAL (
    -- 회차 = 이 도매처가 몇 번째로 받은 주문인가. 7·13 은 300·20 과 서로 소라
    -- 한 도매처의 주문이 날짜와 상태에 고르게 퍼진다.
    SELECT ((g - 1) / :n_wholesaler * 7) % 300  AS day,
           ((g - 1) / :n_wholesaler * 13) % 20  AS bucket
) r;

-- ── 주문 라인 ───────────────────────────────────────────────
INSERT INTO order_item (order_id, variant_id, qty, unit_price, allocated_qty, shipped_qty)
SELECT o.id, v.id, q.qty, 10000 + (o.id % 20) * 500,
       CASE WHEN o.status <> 'CONFIRMED' THEN 0
            WHEN ((o.id - 1) / :n_wholesaler * 13) % 20 >= 12 THEN q.qty
            ELSE GREATEST(q.qty - (o.id % 3), 0) END,
       CASE WHEN o.status = 'CONFIRMED' AND ((o.id - 1) / :n_wholesaler * 13) % 20 >= 12 THEN q.qty ELSE 0 END
FROM orders o
CROSS JOIN LATERAL (SELECT 3 + (o.id % 8) AS qty) q
CROSS JOIN LATERAL (
    SELECT vv.id FROM variant vv
    JOIN product pp ON pp.id = vv.product_id AND pp.wholesaler_id = o.wholesaler_id
    ORDER BY vv.id OFFSET (o.id % 8) LIMIT 3
) v;

-- ── 미송 ────────────────────────────────────────────────────
INSERT INTO backorder (order_item_id, qty, status, created_at)
SELECT oi.id, oi.qty - oi.allocated_qty, 'OPEN', now() - (((oi.id % 30) + 1) || ' day')::interval
FROM order_item oi JOIN orders o ON o.id = oi.order_id
WHERE o.status = 'CONFIRMED' AND oi.qty > oi.allocated_qty;

-- ── 포장 · 봉투 ─────────────────────────────────────────────
INSERT INTO allocation_batch (id, wholesaler_id, created_at)
SELECT o.id, o.wholesaler_id, o.confirmed_at FROM orders o WHERE o.status = 'CONFIRMED';

INSERT INTO outbound (wholesaler_id, partner_id, outbound_number, statement_number, shipped_at, created_at)
SELECT o.wholesaler_id, o.partner_id, o.id,
       CASE WHEN ((o.id - 1) / :n_wholesaler * 13) % 20 >= 12 THEN (o.id % 900) + 1 END,
       CASE WHEN ((o.id - 1) / :n_wholesaler * 13) % 20 >= 12 THEN o.confirmed_at + interval '2 hour' END,
       o.confirmed_at + interval '1 hour'
FROM orders o
WHERE o.status = 'CONFIRMED' AND (((o.id - 1) / :n_wholesaler * 13) % 20 >= 12 OR o.id % 3 = 0);

INSERT INTO packing (order_id, outbound_id, status, created_at)
SELECT o.id, ob.id,
       CASE WHEN ob.shipped_at IS NULL THEN 'READY' ELSE 'PACKED' END,
       o.confirmed_at + interval '1 hour'
FROM orders o
JOIN outbound ob ON ob.outbound_number = o.id AND ob.wholesaler_id = o.wholesaler_id;

INSERT INTO packing (order_id, outbound_id, status, created_at)
SELECT o.id, NULL, 'READY', o.confirmed_at + interval '30 minute'
FROM orders o
WHERE o.status = 'CONFIRMED' AND ((o.id - 1) / :n_wholesaler * 13) % 20 < 12 AND o.id % 3 <> 0;

INSERT INTO packing_item (packing_id, order_item_id, allocation_batch_id, qty)
SELECT pk.id, oi.id, ab.id, GREATEST(oi.allocated_qty, 1)
FROM packing pk
JOIN order_item oi ON oi.order_id = pk.order_id
JOIN allocation_batch ab ON ab.id = pk.order_id
WHERE oi.allocated_qty > 0;

ANALYZE;
