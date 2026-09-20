#!/usr/bin/env bash
# 사전 집계의 쓰기 비용 — 주문 접수 때 요약 갱신이 붙으면 얼마나 느려지나.
#
#   bash write-cost.sh
#
# 두 가지를 잰다.
#   A. 단독 — 주문 1건 접수를 1,000번, 요약 갱신 없음 / 있음
#   B. 경합 — 같은 도매처에 동시 10 세션이 각 100건, 요약 갱신 있음 (같은 요약 행을 다툰다)

set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)/results"
mkdir -p "$DIR"
OUT="$DIR/result-write-cost.txt"
: > "$OUT"

psql() { docker exec -i perf-pg psql -U ondo -d ondo_wholesale -qtA "$@"; }

# 측정용 함수 — 주문 1건 + 라인 3줄, 요약 갱신은 인자로 켠다
psql -c "
SET search_path TO wholesale;
CREATE OR REPLACE FUNCTION perf_place_order(p_wholesaler bigint, p_with_summary boolean)
RETURNS void LANGUAGE plpgsql AS \$\$
DECLARE
    v_order_id bigint;
    v_partner  bigint;
    v_amount   bigint := 0;
    v_bday     date := ((now() AT TIME ZONE 'Asia/Seoul') - interval '12 hour')::date;
BEGIN
    SELECT id INTO v_partner FROM partner WHERE wholesaler_id = p_wholesaler ORDER BY id LIMIT 1;

    INSERT INTO orders (order_number, retail_order_id, partner_id, wholesaler_id, status,
                        payment_term, receive_method, ordered_at)
    VALUES (nextval('perf_order_no'), nextval('perf_order_no'), v_partner, p_wholesaler, 'NEW',
            'CASH', 'RETAILER', now())
    RETURNING id INTO v_order_id;

    INSERT INTO order_item (order_id, variant_id, qty, unit_price)
    SELECT v_order_id, v.id, 5, 20000
    FROM variant v JOIN product p ON p.id = v.product_id AND p.wholesaler_id = p_wholesaler
    ORDER BY v.id LIMIT 3;

    v_amount := 3 * 5 * 20000;

    IF p_with_summary THEN
        INSERT INTO dashboard_daily (wholesaler_id, business_day, order_count, order_amount)
        VALUES (p_wholesaler, v_bday, 1, v_amount)
        ON CONFLICT (wholesaler_id, business_day)
        DO UPDATE SET order_count  = dashboard_daily.order_count + 1,
                      order_amount = dashboard_daily.order_amount + EXCLUDED.order_amount;
    END IF;
END;
\$\$;
CREATE SEQUENCE IF NOT EXISTS perf_order_no START 90000000;
" > /dev/null

bench() {   # bench <라벨> <with_summary> <횟수>
    local label="$1" with="$2" n="$3" per
    per=$(psql -c "
      SET search_path TO wholesale;
      SELECT round(extract(epoch from (
               SELECT clock_timestamp() - t0 FROM (
                 SELECT clock_timestamp() AS t0, count(perf_place_order(1, $with)) AS _
                 FROM generate_series(1, $n)
               ) x
             )) * 1000 / $n, 3);" | tr -d ' ')
    printf '%-24s %8s ms/건  (총 %s건)\n' "$label" "$per" "$n" | tee -a "$OUT"
}

echo "A. 단독 접수" | tee -a "$OUT"
bench "요약 갱신 없음" false 1000
bench "요약 갱신 있음" true 1000

echo | tee -a "$OUT"
echo "B. 같은 도매처 동시 접수 (10 세션 × 100건, 요약 갱신 있음)" | tee -a "$OUT"
start=$(python3 -c "import time; print(time.time())")
for i in $(seq 1 10); do
    docker exec -i perf-pg psql -U ondo -d ondo_wholesale -qtA -c "
      SET search_path TO wholesale;
      SELECT perf_place_order(1, true) FROM generate_series(1, 100);" > /dev/null &
done
wait
end=$(python3 -c "import time; print(time.time())")
python3 -c "
t = ($end - $start) * 1000
print(f'{\"동시 10 세션\":<24} {t/1000:8.3f} ms/건  (총 1000건 · 벽시계 {t:.0f} ms)')" | tee -a "$OUT"

echo | tee -a "$OUT"
echo "→ $OUT"
