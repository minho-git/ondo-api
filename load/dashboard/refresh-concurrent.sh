#!/usr/bin/env bash
# 재계산 동시 처리량 — 세션 N 개가 서로 다른 도매처를 동시에 재계산한다.
#
#   bash refresh-concurrent.sh [세션수] [세션당_횟수]
#
# 왜 재나 — 대시보드를 켜 둔 도매처는 갱신 주기(20초)마다 자기 요약을 다시 만든다.
# 3만 곳 중 동시 접속이 10% 면 3,000 곳 ÷ 20초 = 초당 150 회. 이걸 DB 가 받아내는지가
# 캐시가 필요한지 아닌지를 가른다.

set -euo pipefail
SESSIONS=${1:-20}
PER=${2:-100}
DIR=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)
OUT=$(mktemp -d)

echo "세션 ${SESSIONS} × ${PER} 회 = $((SESSIONS * PER)) 재계산"

start=$(python3 -c 'import time; print(time.time())')

for s in $(seq 1 "$SESSIONS"); do
    # 세션마다 다른 도매처 구간을 맡는다 — 같은 행을 두고 다투지 않게
    from=$(( 2000 + (s - 1) * PER ))
    to=$(( from + PER - 1 ))
    docker exec -i perf-pg psql -U ondo -d ondo_wholesale -q -c "
        SET search_path TO wholesale;
        SELECT count(wholesale.perf_refresh_one(g)) FROM generate_series($from, $to) g;
    " > "$OUT/$s.log" 2>&1 &
done
wait

end=$(python3 -c 'import time; print(time.time())')

python3 - "$start" "$end" "$SESSIONS" "$PER" <<'PY'
import sys
start, end, sessions, per = float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
total = sessions * per
sec = end - start
print(f"걸린 시간 {sec:.2f}s · 재계산 {total} 회 · 초당 {total/sec:.0f} 회 · 1 회당 {sec/total*1000:.2f} ms")
print(f"→ 3만 곳 전부가 20초마다 갱신하면 초당 1,500 회 필요. 여유 {total/sec/1500:.2f} 배")
PY

grep -il error "$OUT"/*.log && echo "에러 있음 — $OUT 확인" || true
