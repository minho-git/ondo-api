#!/usr/bin/env bash
# 기준선 한 벌을 돌린다 (MUL-118).
#
#   ./run-baseline.sh small
#
# 1. 데이터를 새로 넣는다 — 앞선 실행이 만든 주문·장바구니가 결과에 안 섞이게
# 2. API 13개를 하나씩 — 초당 20건 · 30초
# 3. 섞어서 — 초당 50 → 100 → 200 → 400 행동 · 각 60초
# 4. 표로 정리 — results/<규모>/summary.md
#
# 소매(8080)·도매(8081)가 로컬에 떠 있어야 한다. 돌리는 동안 다른 작업은 피한다 —
# 부하기와 앱과 DB 가 한 노트북의 CPU 를 나눠 쓴다.
set -euo pipefail
cd "$(dirname "$0")"

scale="${1:-small}"
single_rate="${SINGLE_RATE:-20}"
single_duration="${SINGLE_DURATION:-30s}"
mixed_rates="${MIXED_RATES:-50 100 200 400}"
mixed_duration="${MIXED_DURATION:-60s}"

./data/load.sh "$scale"
# 앞선 실행·확인용 실행의 결과가 요약에 섞이지 않게 비우고 시작한다
rm -rf "results/$scale" results/single-"$scale"-*.json results/mixed-"$scale"-*.json
mkdir -p "results/$scale"

for api in listings listingDetail categories filterOptions cartList cartAdd cartCount me \
           orderList orderDetail backorders checkout placeOrder; do
  k6 run --quiet -e SCALE="$scale" -e API="$api" -e RATE="$single_rate" -e DURATION="$single_duration" single.js
done

for rate in $mixed_rates; do
  k6 run --quiet -e SCALE="$scale" -e RATE="$rate" -e DURATION="$mixed_duration" mixed.js
done

mv results/single-"$scale"-*.json results/mixed-"$scale"-*.json "results/$scale/"
python3 summarize.py "$scale"
