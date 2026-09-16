#!/usr/bin/env bash
# 부하 테스트 데이터를 로컬 DB 두 대에 넣는다 (MUL-118).
#
#   ./load.sh small      도매처 50 · 상품 1천 · 소매처 100 · 주문서 1천
#   ./load.sh medium     도매처 200 · 상품 1만 · 소매처 500 · 주문서 1만
#   ./load.sh large      도매처 500 · 상품 10만 · 소매처 1천 · 주문서 10만
#
# 로컬 전용이다. db/compose.yml 로 띄운 컨테이너에만 넣는다 — 배포 DB 는 건드릴 길이 없다.
#
# 앱을 먼저 한 번 띄워둬야 한다. 스키마는 Flyway 가 만든다.
# 다시 돌리면 이전 부하 데이터(id 1,000,000 이상)를 지우고 새로 넣는다.
set -euo pipefail

cd "$(dirname "$0")"
scale="${1:-small}"

python3 generate.py "$scale"

echo "도매 DB 에 넣는 중..."
docker exec -i ondo-wholesale-db psql -q -o /dev/null -U ondo -d ondo_wholesale < out/wholesale.sql

echo "소매 DB 에 넣는 중..."
docker exec -i ondo-retail-db psql -q -o /dev/null -U ondo -d ondo_retail < out/retail.sql

echo "완료 — 로그인 load-r000001@ondo.test / ondo1234!"
