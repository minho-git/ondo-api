#!/usr/bin/env bash
# 사전 집계 적용 후 측정 — 느린 셋(오늘주문 · 미송 · 포장대기)만 요약 테이블에서 읽는다.
# 나머지 셋은 이미 1ms 아래라 원래 쿼리 그대로 둔다.
#
#   bash measure-summary.sh [라벨]

set -euo pipefail
LABEL="${1:-summary}"
DIR="$(cd "$(dirname "$0")" && pwd)/results"
mkdir -p "$DIR"
OUT="$DIR/result-$LABEL.txt"
PSQL=(docker exec -i perf-pg psql -U ondo -d ondo_wholesale -qtA)

WID=1
START="now() - interval '6 hour'"
BDAY="((now() AT TIME ZONE 'Asia/Seoul') - interval '12 hour')::date"

run() {
    local name="$1" sql="$2" times=() t
    for _ in 1 2 3 4 5; do
        t=$("${PSQL[@]}" -c "EXPLAIN (ANALYZE, TIMING OFF) $sql" | grep 'Execution Time' | sed 's/[^0-9.]//g')
        times+=("$t")
    done
    printf '%-14s %8s ms\n' "$name" "$(printf '%s\n' "${times[@]}" | sort -n | sed -n '3p')" | tee -a "$OUT"
}

: > "$OUT"
echo "[$LABEL] 대시보드 집계 — 중앙값(5회)" | tee -a "$OUT"

# 신규주문 — 개수는 요약에서 읽고, 가장 오래된 1건만 인덱스로 찾는다
run "신규주문" "select (select new_count from wholesale.dashboard_counter where wholesaler_id = $WID) as cnt,
       o.ordered_at, pt.retailer_name
from wholesale.orders o join wholesale.partner pt on pt.id = o.partner_id
where o.wholesaler_id = $WID and o.status = 'NEW'
order by o.ordered_at asc, o.id asc limit 1"

run "출고봉투" "select count(*) as not_shipped, count(*) filter (where ob.created_at < $START) as stale
from wholesale.outbound ob where ob.wholesaler_id = $WID and ob.shipped_at is null"

run "오늘출고" "select count(distinct ob.id) as cnt, coalesce(sum(pi.qty), 0) as qty
from wholesale.outbound ob
left join wholesale.packing pk on pk.outbound_id = ob.id
left join wholesale.packing_item pi on pi.packing_id = pk.id and pi.deleted_at is null
where ob.wholesaler_id = $WID and ob.shipped_at >= $START"

# 요약 테이블에서 읽는 셋
run "오늘주문" "select coalesce(order_count, 0) as cnt, coalesce(order_amount, 0) as amount,
       coalesce(cancelled_count, 0) as cancelled
from wholesale.dashboard_daily
where wholesaler_id = $WID and business_day = $BDAY"

run "포장대기" "select count(*) as retailer_count, coalesce(sum(qty), 0) as qty
from (select retailer_id, sum(qty) as qty from wholesale.dashboard_packing_queue
      where wholesaler_id = $WID and qty > 0 group by retailer_id) s"

run "미송" "select count(*) as sku_count, coalesce(sum(s.open_qty), 0) as qty,
       count(*) filter (where v.expected_inbound_date < current_date) as overdue,
       count(*) filter (where v.expected_inbound_date is null) as no_date
from wholesale.dashboard_backorder_sku s
join wholesale.variant v on v.id = s.variant_id
where s.wholesaler_id = $WID and s.open_qty > 0"

echo "합계 $(awk '/ms$/ {s+=$2} END {printf "%.1f", s}' "$OUT") ms" | tee -a "$OUT"
