#!/usr/bin/env bash
# 대시보드 summary 집계 6개를 재는 스크립트.
#
#   bash measure.sh [라벨]
#
# 각 쿼리를 5회 실행해 중앙값(ms)을 찍고, EXPLAIN (ANALYZE, BUFFERS) 요약을 남긴다.
# psql \timing 대신 EXPLAIN ANALYZE 의 Execution Time 을 쓴다 — 네트워크·파싱 시간을 뺀 순수 실행 시간.

set -euo pipefail
LABEL="${1:-baseline}"
DIR="$(cd "$(dirname "$0")" && pwd)/results"
mkdir -p "$DIR"
OUT="$DIR/result-$LABEL.txt"
PSQL=(docker exec -i perf-pg psql -U ondo -d ondo_wholesale -qtA)

WID=1                                    # 주문이 몰린 도매처
START="now() - interval '6 hour'"        # 영업일 시작(낮 12시) 대역

run() {                                  # run <이름> <SQL>
    local name="$1" sql="$2" times=() t
    for _ in 1 2 3 4 5; do
        t=$("${PSQL[@]}" -c "EXPLAIN (ANALYZE, TIMING OFF) $sql" | grep 'Execution Time' | sed 's/[^0-9.]//g')
        times+=("$t")
    done
    local median
    median=$(printf '%s\n' "${times[@]}" | sort -n | sed -n '3p')
    printf '%-14s %8s ms\n' "$name" "$median" | tee -a "$OUT"
}

plan() {                                 # plan <이름> <SQL> — 실행계획 요약
    {
        echo "── $1"
        "${PSQL[@]}" -c "EXPLAIN (ANALYZE, BUFFERS, TIMING OFF) $2" \
            | grep -E 'Seq Scan|Index Scan|Index Only Scan|Bitmap|Sort |Hash Join|Nested Loop|Execution Time|shared read|shared hit' \
            | head -12
        echo
    } >> "$DIR/plan-$LABEL.txt"
}

: > "$OUT"; : > "$DIR/plan-$LABEL.txt"
echo "[$LABEL] 대시보드 집계 — 중앙값(5회)" | tee -a "$OUT"

Q_NEW="select count(*) over() as cnt, o.ordered_at, pt.retailer_name
from wholesale.orders o join wholesale.partner pt on pt.id = o.partner_id
where o.wholesaler_id = $WID and o.status = 'NEW'
order by o.ordered_at asc, o.id asc limit 1"

Q_TODAY="select count(distinct o.id) as cnt,
       coalesce(sum(oi.qty * oi.unit_price), 0) as amount,
       count(distinct o.id) filter (where o.status = 'CANCELLED') as cancelled
from wholesale.orders o left join wholesale.order_item oi on oi.order_id = o.id
where o.wholesaler_id = $WID and o.ordered_at >= $START"

Q_PACKING="select count(distinct pt.retailer_id) as retailer_count, coalesce(sum(pi.qty), 0) as qty
from wholesale.packing_item pi
join wholesale.packing pk on pk.id = pi.packing_id
join wholesale.orders o on o.id = pk.order_id
join wholesale.partner pt on pt.id = o.partner_id
where o.wholesaler_id = $WID and pk.status = 'READY' and pk.outbound_id is null and pi.deleted_at is null"

Q_OUTBOUND="select count(*) as not_shipped, count(*) filter (where ob.created_at < $START) as stale
from wholesale.outbound ob
where ob.wholesaler_id = $WID and ob.shipped_at is null"

Q_SHIPPED="select count(distinct ob.id) as cnt, coalesce(sum(pi.qty), 0) as qty
from wholesale.outbound ob
left join wholesale.packing pk on pk.outbound_id = ob.id
left join wholesale.packing_item pi on pi.packing_id = pk.id and pi.deleted_at is null
where ob.wholesaler_id = $WID and ob.shipped_at >= $START"

Q_BACKORDER="select count(distinct v.id) as sku_count,
       coalesce(sum(oi.qty - oi.allocated_qty), 0) as qty,
       count(distinct v.id) filter (where v.expected_inbound_date < current_date) as overdue,
       count(distinct v.id) filter (where v.expected_inbound_date is null) as no_date
from wholesale.backorder b
join wholesale.order_item oi on oi.id = b.order_item_id
join wholesale.orders o on o.id = oi.order_id
join wholesale.variant v on v.id = oi.variant_id
where b.status = 'OPEN' and o.wholesaler_id = $WID"

for pair in "신규주문:Q_NEW" "오늘주문:Q_TODAY" "포장대기:Q_PACKING" "출고봉투:Q_OUTBOUND" "오늘출고:Q_SHIPPED" "미송:Q_BACKORDER"; do
    name="${pair%%:*}"; var="${pair##*:}"
    run "$name" "${!var}"
    plan "$name" "${!var}"
done

echo "합계 $(awk '/ms$/ {s+=$2} END {printf "%.1f", s}' "$OUT") ms" | tee -a "$OUT"
echo "→ $OUT · plan-$LABEL.txt"
