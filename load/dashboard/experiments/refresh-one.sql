-- 도매처 한 곳 재계산 비용 — DashboardSummaryRefresher.refresh(wholesalerId) 와 같은 SQL.
--
--   docker exec -i perf-pg psql -U ondo -d ondo_wholesale -f - < refresh-one.sql
--
-- 이게 재는 것 — 대시보드를 켜 둔 도매처는 갱신 주기(20초)마다 자기 것만 다시 계산한다.
-- 3만 곳 중 몇 %가 동시에 보고 있느냐에 이 값을 곱하면 초당 필요한 재계산 처리량이 나온다.

\set ON_ERROR_STOP on
SET search_path TO wholesale;

CREATE OR REPLACE FUNCTION perf_refresh_one(w bigint) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM dashboard_daily WHERE wholesaler_id = w;
    INSERT INTO dashboard_daily (wholesaler_id, business_day, order_count, order_amount, cancelled_count)
    SELECT o.wholesaler_id,
           ((o.ordered_at AT TIME ZONE 'Asia/Seoul') - interval '12 hour')::date,
           count(DISTINCT o.id), coalesce(sum(oi.qty * oi.unit_price), 0),
           count(DISTINCT o.id) FILTER (WHERE o.status = 'CANCELLED')
    FROM orders o LEFT JOIN order_item oi ON oi.order_id = o.id
    WHERE o.wholesaler_id = w
    GROUP BY 1, 2;

    INSERT INTO dashboard_counter (wholesaler_id, new_count, refreshed_at)
    SELECT w, (SELECT count(*) FROM orders WHERE wholesaler_id = w AND status = 'NEW'), now()
    ON CONFLICT (wholesaler_id)
    DO UPDATE SET new_count = excluded.new_count, refreshed_at = excluded.refreshed_at;

    DELETE FROM dashboard_backorder_sku WHERE wholesaler_id = w;
    INSERT INTO dashboard_backorder_sku (wholesaler_id, variant_id, open_qty, oldest_created_at)
    SELECT o.wholesaler_id, oi.variant_id, sum(oi.qty - oi.allocated_qty), min(b.created_at)
    FROM backorder b
    JOIN order_item oi ON oi.id = b.order_item_id
    JOIN orders o      ON o.id = oi.order_id
    WHERE b.status = 'OPEN' AND o.wholesaler_id = w
    GROUP BY 1, 2 HAVING sum(oi.qty - oi.allocated_qty) > 0;

    DELETE FROM dashboard_packing_queue WHERE wholesaler_id = w;
    INSERT INTO dashboard_packing_queue (wholesaler_id, retailer_id, receive_method, qty)
    SELECT o.wholesaler_id, pt.retailer_id, o.receive_method, sum(pi.qty)
    FROM packing_item pi
    JOIN packing pk ON pk.id = pi.packing_id
    JOIN orders o   ON o.id = pk.order_id
    JOIN partner pt ON pt.id = o.partner_id
    WHERE o.wholesaler_id = w
      AND pk.status = 'READY' AND pk.outbound_id IS NULL AND pi.deleted_at IS NULL
    GROUP BY 1, 2, 3;
END $$;

-- 캐시 데우기 (첫 호출의 디스크 읽기를 측정에서 뺀다)
SELECT count(perf_refresh_one(g)) AS warmup FROM generate_series(1, 50) g;

-- 도매처 500 곳을 한 곳씩 재계산하고 평균·중앙값·최대를 낸다
DO $$
DECLARE
    t0 timestamptz; ms double precision; arr double precision[] := '{}';
    m_avg numeric; m_p50 numeric; m_max numeric; m_n bigint;
BEGIN
    FOR w IN 1001..1500 LOOP
        t0 := clock_timestamp();
        PERFORM perf_refresh_one(w);
        ms := extract(epoch FROM clock_timestamp() - t0) * 1000;
        arr := arr || ms;
    END LOOP;
    SELECT round(avg(x)::numeric, 2),
           round(percentile_cont(0.5) WITHIN GROUP (ORDER BY x)::numeric, 2),
           round(max(x)::numeric, 2), count(*)
    INTO m_avg, m_p50, m_max, m_n
    FROM unnest(arr) x;
    RAISE NOTICE '재계산 1회 — 평균 % ms · 중앙값 % ms · 최대 % ms · 표본 %', m_avg, m_p50, m_max, m_n;
END $$;
