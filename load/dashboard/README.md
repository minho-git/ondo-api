# 대시보드 집계 성능 — 측정 기록

로컬 컨테이너(PostgreSQL 16, `perf-pg`)에서 잰 값. 배포 환경은 건드리지 않았다.
MUL-132 의 측정 자산이다 — 시드·측정 스크립트와 기준선을 여기 남긴다(MUL-133).

```
load/dashboard/
  README.md            이 문서 — 기준선 표와 실행계획 요약
  data/seed.sql        규모·분포를 인자로 받는 시드
  experiments/         인덱스·사전 집계·재계산 SQL
  measure*.sh          집계 6종을 EXPLAIN ANALYZE 로 5회씩
  write-cost.sh        사전 집계의 쓰기 대가
  refresh-concurrent.sh  재계산 동시 처리량
  dashboard-poll.js    k6 폴링 부하
  baseline/            이 문서가 인용하는 원자료 (커밋한다)
  results/             재실행이 새로 쌓는 출력 (커밋하지 않는다)
```

> `load/retail/` 은 `results/` 를 통째로 무시하지만 여기는 `baseline/` 을 커밋한다 —
> 이 사례는 결과 자체가 산출물이라 인용한 숫자의 출처가 리포에 있어야 한다.

**사전 집계 부분은 V13 마이그레이션(`dashboard_*` 네 표)이 있어야 돈다.** `dev` 에 아직 없으면
인덱스까지만 재현된다. 그 전 단계를 혼자 재현하려면 `experiments/summary-prototype.sql` 을 쓴다.

## 왜 이 규모인가

동대문 하루 거래액 600억 ÷ 도매 3만 곳 = 도매 한 곳 하루 200만 원. 주문 한 건을 10만 원으로 보면
**하루 20건**, 연 300일이면 **6,000건**이다. 상가 하나(도매 500곳)는 **1년에 300만 건**이 된다.

| 규모 | 뜻 |
|---|---|
| 30만 | 상가 1곳 1개월 |
| 300만 | 상가 1곳 1년 |

건당 10만 원은 가정이다. 계산 과정을 남겨 두면 숫자를 바꿔 따져볼 수 있다.

---

## 1. SQL 단위 — 집계 6종 합계 (중앙값 5회)

### 주문 300만 · 도매처 500곳 균등

| 집계 | 인덱스 없음 | 인덱스 | 사전 집계 | + 개수 분리 |
|---|---:|---:|---:|---:|
| 오늘 주문 | 153.0 | 29.5 | 0.02 | 0.02 |
| 미송 | 154.6 | 24.9 | 0.04 | 0.04 |
| 포장 대기 | 21.3 | 7.5 | 0.04 | 0.04 |
| 신규 주문 | 6.5 | 5.5 | 5.0 | **0.04** |
| 오늘 출고 | 18.5 | 0.05 | 0.05 | 0.05 |
| 출고 봉투 | 0.03 | 0.03 | 0.03 | 0.03 |
| **합계** | **354.0** | **67.5** | **5.2** | **0.2 ms** |

### 규모별 (인덱스 전후)

| 주문 수 | 인덱스 없음 | 인덱스 |
|---|---:|---:|
| 20만 · 한 도매처에 절반 몰림 | 115.4 | 79.7 |
| 30만 · 균등 | 61.1 | 8.3 |
| 300만 · 균등 | 354.0 | 67.5 |

**분포가 인덱스 효용을 결정한다.** 한 도매처가 전체의 절반을 차지하면 플래너가 인덱스를 쓰지 않는다 —
걸러도 절반이 남으니 전체 훑기가 더 싸기 때문이다. 도매처 500곳에 고르게 두자 인덱스가 먹기 시작했다.

---

## 2. 실행계획에서 본 것

**인덱스 없음** — `Seq Scan on orders / order_item / packing_item`. 조건은 "내 도매처"인데
거기로 가는 길이 없어 전체를 훑고 하나씩 대조한다.

**인덱스 후에도 남은 것** — 오늘 주문은 `Bitmap Index Scan`으로 6,000건을 잘 찾지만,
주문 라인을 붙이면 18,000줄이 되고 그걸 읽어 합산한다(`Buffers: read=11,388` ≈ 89MB).
미송은 네 테이블 조인이라 84,000블록(≈650MB)을 만진다.

> 인덱스는 **찾는 양**을 줄이지만 **세는 일**은 그대로다. 30초마다 같은 계산을 반복한다.

**신규 주문이 안 빨라진 이유** — `count(*) over()` 때문에 `LIMIT 1`인데도 6,000행을 다 읽는다.
개수를 요약 테이블로 떼어내자 5.0 → 0.04ms.

---

## 3. 사전 집계 설계

| 표 | 키 | 값 | 갱신 시점 |
|---|---|---|---|
| `dashboard_daily` | 도매처 · 영업일 | 주문 수 · 금액 · 취소 수 | 주문 접수 · 취소 |
| `dashboard_counter` | 도매처 | 확정 대기 수 | 주문 상태 전이 |
| `dashboard_backorder_sku` | 도매처 · SKU | 남은 미송 장수 | 미송 생성 · 배분 · 취소 |
| `dashboard_packing_queue` | 도매처 · 소매처 · 수령방식 | 대기 장수 | 배분 · 포장 · 출고 |

**입고일 지남·미등록은 세어두지 않는다.** 날짜가 바뀌면 저절로 변하는 값이라, SKU 단위 잔여량만 들고
날짜 판정은 읽을 때 한다. 그래야 매일 다시 계산하는 배치가 필요 없다.

**소매처 수는 카운터로 두지 않는다.** 소매처를 행으로 두면 세는 대신 행 수를 읽으면 된다.

읽는 행이 이렇게 줄었다.

| | 읽는 행 |
|---|---|
| 원래 | 주문 300만 · 라인 900만 · 미송 270만 |
| 요약 | 1,450 + 1,350 + 225행 |

---

## 4. 쓰기 비용 (대가)

주문 접수 2,000건을 한 세션에서 연속 실행.

| | 건당 | 차이 |
|---|---:|---:|
| 요약 갱신 없음 | 0.049 ms | — |
| 요약 갱신 있음 | 0.056 ms | **+0.007 ms (+14%)** |
| 같은 도매처 동시 10세션 | 0.147 ms | 같은 요약 행 경합 포함 |

읽기를 1,770배 줄이는 대가로 쓰기가 14% 늘었다. 대시보드는 도매처마다 30초에 한 번,
주문은 도매처당 하루 20건이다. 빈도 차이를 생각하면 남는 거래다.

---

## 5. 동시 폴링 부하 (API 단위)

k6, 도매처가 30초마다 대시보드를 부르는 조건. **당시 코드(인덱스만, 사전 집계 전)** 기준 — 6절이 사전 집계 후 값이다.

| 도매처 | 초당 요청 | p95 |
|---:|---:|---:|
| 200곳 | 6.7건 | 104 ms |
| 500곳 | 16.7건 | 116 ms |
| **2,000곳** | **66.7건** | **8.35 초** |

500곳까지는 버티다가 2,000곳에서 무너진다. 한 건이 80ms여도 초당 67건이 들어오면
커넥션이 마르고 대기가 쌓인다. **한 건의 속도와 동시성은 다른 문제다.**

---

## 6. 동대문 전체 규모 — 도매처 3만 곳 · 주문 900만

앞 절까지는 상가 한 곳(도매 500곳)이었다. 캐시가 필요한지 가리려고 전국 규모로 올렸다.

도매처마다 300일에 걸쳐 300건. 날짜·상태는 **회차**(그 도매처의 몇 번째 주문인가)에서 뽑는다 —
`g % 도매처수` 와 약수가 겹치면 한 도매처의 주문이 한 날짜·한 상태로 몰린다(처음에 그렇게 틀렸다).

| 표 | 행 | 크기 |
|---|---:|---:|
| `order_item` | 2,700만 | 3,402 MB |
| `orders` | 900만 | 2,531 MB |
| `packing_item` | 2,025만 | 2,385 MB |
| `backorder` | 810만 | 1,008 MB |
| **`dashboard_daily`** | **900만** | **786 MB** |
| `dashboard_backorder_sku` | 6만 | 6.3 MB |
| `dashboard_counter` | 3만 | 2.0 MB |
| `dashboard_packing_queue` | 2만 | 2.6 MB |

**요약 네 표 합계 797MB.** 이 중 786MB가 `dashboard_daily` 다 — 키가 (도매처 · 영업일)이라
행 수가 `3만 × 보관일수` 로 늘고, **주문이 몇 건이든 똑같다.** 나머지 셋은 도매처 수에 비례해
3만~6만 행, 다 합쳐 11MB다. 화면은 오늘 한 줄만 PK 로 읽으므로 크기와 무관하게 0.02ms.

### 재계산 비용

| | 시간 |
|---|---:|
| 3만 곳 전체 백필 (한 번에) | **32 초** |
| 도매처 1곳 재계산 (중앙값 500회) | 2.27 ms |
| └ 미송·포장이 있는 도매처만 (334곳) | **2.57 ms** |
| 그중 오늘치만 다시 만들기로 좁히면 | 2.04 ms |

> **시드 치우침** — `30000 % 3 == 0` 이라 한 도매처의 주문 id 는 3으로 나눈 나머지가 고정된다.
> 미송·포장을 `id % 3` 으로 가르는 바람에 도매처의 **1/3 은 미송·포장이 아예 없다**(2만/3만).
> 그 몫이 공짜라 평균이 낮게 잡혔다. 값이 있는 도매처만 추리면 2.57ms 로, 결론은 그대로다.

한 곳 2.27ms 를 쪼개면 미송 0.78 · 포장 0.40 · 오늘주문 0.16 · 확정대기 0.01ms 다.
**미송이 절반이다** — 도매처당 미송 270행을 세 테이블 조인으로 다시 센다.

`dashboard_daily` 를 오늘치로 좁혀도 2.27 → 2.04ms 뿐이다. 범위를 줄여봐야
미송·포장이 남아서, 여기서 더 내려가려면 **다시 세지 않는 방법**(증분 갱신)이어야 한다.

동시 재계산 처리량(서로 다른 도매처, 세션 수를 올리며):

| 동시 세션 | 초당 재계산 |
|---:|---:|
| 20 | **693** |
| 40 | 597 |
| 80 | 307 |

천장은 **초당 약 700회**다. 3만 곳이 **전부** 화면을 켜두면 20초 주기로 초당 1,500회가
필요하니 모자란다. 동시 접속 10~20%(3,000~6,000곳)면 초당 150~300회 — 2.3~4.6배 여유다.

### API 폴링 (도매처 1,000곳이 각자 다른 요약을 본다)

세션을 서로 다른 도매처로 만드는 게 중요하다. 50개로 돌리면 같은 요약만 읽어서
재계산 부하가 측정에서 사라진다.

| 조건 | 초당 요청 | 중앙값 | p95 | 실패 |
|---|---:|---:|---:|---:|
| 재계산 **끔**(주기 1시간) | 222 | 5.26 ms | 54.8 ms | 0 |
| 재계산 **켬**(주기 20초) | 222 | 5.74 ms | 54.8 ms | 0 |

같은 부하에서 **차이가 0.5ms** 다. 켠 쪽에서 1,000곳 전부의 `refreshed_at` 이 실제로 갱신된 걸
확인했으니 재계산이 빠진 게 아니다. 더 올리면(초당 1,000 요청) 둘 다 417 / 427 req/s 에서
막히는데, 이건 설계가 아니라 **한 노트북에 Postgres · JVM · k6 를 같이 올린 탓**이다
(무부하 단건은 8~11ms, 부하 시 356ms — 대부분 큐 대기).

---

## 7. 부하 시험에서 잡은 결함 둘

둘 다 단위·통합 테스트 633개를 통과한 코드였다. 실제 HTTP 경로에 동시 요청을 넣고서야 나왔다.

**① 읽기 전용 트랜잭션 안에서 DELETE** — 요청의 98%가 500.
`refreshOnce` 에 `@Transactional(REQUIRES_NEW)` 를 걸었지만 같은 클래스 안에서 부르고 있었다.
스프링 프록시를 안 거치니 애노테이션이 무시되고, 조회의 `readOnly = true` 트랜잭션에서
그대로 DELETE 가 돌았다. → 잠금·갱신 트랜잭션을 별도 빈으로 뺐다.

통합 테스트가 못 잡은 이유는 둘이다. 테스트가 `refresher.refresh()` 를 직접 불러 이 경로를
건너뛰었고, 테스트 자체가 `@Transactional` 이라 어차피 읽기 전용이 아니었다.
→ 대시보드 통합 테스트를 커밋하고 도는 방식으로 바꿔 실제 경로를 타게 했다.

**② 커넥션 풀 자기 교착** — 실패는 없지만 처리량이 11 req/s 로 무너지고 30초 타임아웃.

```
HikariPool-1 - Connection is not available, request timed out after 30003ms
(total=10, active=10, idle=0, waiting=189)
```

①을 고쳐 `REQUIRES_NEW` 가 실제로 걸리자 드러났다. 조회가 읽기 트랜잭션으로 커넥션 하나를
쥔 채 갱신이 **두 번째**를 요구한다. 풀이 10이면 요청 10개가 각자 하나씩 쥐고 서로의 두 번째를
기다린다. 아무도 못 놓으니 전부 타임아웃이다.

→ 갱신을 읽기 트랜잭션 **밖**으로 옮겨 차례로 돌게 했다(`DashboardQueryService` 는 트랜잭션 없음,
갱신 트랜잭션 → 읽기 트랜잭션 순). 한 번에 커넥션 하나만 쓴다. 고친 뒤 같은 부하에서 실패 0.

> 풀 크기를 늘리는 건 답이 아니다. 요청당 커넥션 2개를 쓰는 한 부하가 커지면 같은 교착이 난다.

---

## 8. 결론 — 캐시는 넣지 않는다

| | 측정값 |
|---|---|
| 요약 읽기 | 0.2 ms (SQL) · 5.7 ms (HTTP 왕복) |
| 재계산 1회 | 2.57 ms |
| 재계산을 켜고 끈 응답 차이 | **0.5 ms** |

값 검산 — 도매처 2·3·7 의 API 응답과 원본 직접 집계가 여섯 항목 모두 일치한다
(신규 45 · 포장 소매처 1 / 1,215장 · 미출고 봉투 · 미송 3 SKU / 405·810장 · 오늘 주문).

**Redis 를 넣으면 무엇이 좋아지나** — 읽기가 1.5ms 에서 0.3ms 가 된다. 그런데 읽기는 병목이
아니다(끄나 켜나 0.5ms 차이). 재계산은 캐시가 대신 해주지 않으니 그대로다.

**이미 캐시를 쓰고 있다.** `dashboard_counter.refreshed_at` + 20초 신선도 기준이 TTL 캐시고,
`pg_try_advisory_xact_lock` 이 스탬피드 방어다. 저장소가 Redis 가 아니라 Postgres 일 뿐이다.
Redis 로 옮기면 네 표를 한 트랜잭션으로 바꾸던 원자성을 잃고, 장애 시 복구 경로와
운영 요소가 하나 늘고, 병목이 아닌 구간에서 1ms 를 번다. **근거가 안 선다.**

**한계에 닿으면 답은 캐시가 아니라 증분 갱신이다.** 3만 곳이 전부 화면을 켜두는 상황에서만
초당 1,500회가 필요해 천장(700)을 넘는다. 그때 고칠 것은 읽기가 아니라 "매번 다시 센다"는
쪽이다 — 주문이 들어올 때 카운터를 +1 하면 재계산 자체가 사라진다. 지금 넣지 않는 이유는
정확성 대가가 크기 때문이다. 다시 세는 방식은 어긋나도 다음 갱신에 저절로 맞지만,
증분은 한 번 틀리면 계속 틀린 채로 간다.

---

## 다음

- [x] 사전 집계를 앱 코드에 반영하고 같은 부하 재측정
- [x] 동대문 전체(3만 곳) 규모에서 재계산·폴링 측정 — 캐시 판단
- [ ] 미송 재계산(2.27ms 중 0.78ms)을 증분으로 바꿀지 — 동시 접속이 3만 곳의 20%를 넘으면
- [ ] 요약값과 원본 집계가 같은지 검산하는 테스트, 어긋났을 때 재계산 수단

## 재현 방법

모두 `load/dashboard/` 에서 돈다. 부하기와 앱과 DB 가 한 노트북의 CPU 를 나눠 쓰므로
돌리는 동안 다른 작업은 피한다.

```bash
# 1. 컨테이너와 스키마
docker run -d --name perf-pg -e POSTGRES_PASSWORD=ondo -e POSTGRES_USER=ondo \
  -e POSTGRES_DB=ondo_wholesale -p 55500:5432 postgres:16
for f in $(ls ../../wholesale-api/src/main/resources/db/migration/*.sql | sort -V); do
  docker exec -i perf-pg psql -U ondo -d ondo_wholesale -q < "$f"; done

# 2. 데이터 — 규모·분포를 인자로 준다 (skew=1 이면 1번 도매처에 절반)
docker exec -i perf-pg psql -U ondo -d ondo_wholesale -q \
  -v n_orders=3000000 -v n_wholesaler=500 -v skew=0 -f - < data/seed.sql

# 3. 단계별 측정 — 라벨마다 results/result-<라벨>.txt 와 results/plan-<라벨>.txt 가 나온다
bash measure.sh 3m-noidx
docker exec -i perf-pg psql -U ondo -d ondo_wholesale -q < experiments/index-01.sql
bash measure.sh 3m-idx
bash measure-summary.sh 3m-summary     # V13 의 dashboard_* 네 표가 있어야 한다
bash write-cost.sh                     # 사전 집계의 쓰기 대가
```

`measure.sh` 를 두 번 잴 때 주의 — `TRUNCATE` 는 인덱스를 지우지 않는다.
인덱스 없는 조건으로 돌아가려면 `experiments/drop-index.sql` 을 먼저 돌린다.

### 3만 도매처 규모 (6~8절)

```bash
# 시드 11분. 날짜·상태를 회차에서 뽑으므로 도매처마다 고르게 퍼진다
docker exec -i perf-pg psql -U ondo -d ondo_wholesale -q \
  -v n_orders=9000000 -v n_wholesaler=30000 -v skew=0 -f - < data/seed.sql

docker exec -i perf-pg psql -U ondo -d ondo_wholesale -f - < experiments/refresh-all.sql
docker exec -i perf-pg psql -U ondo -d ondo_wholesale -f - < experiments/refresh-one.sql
bash refresh-concurrent.sh 20 100
```

API 폴링을 재려면 앱을 이 컨테이너에 붙여 띄운다. 시드가 id 를 직접 넣으므로
**띄우기 전에 시퀀스를 밀어야 한다** — 안 그러면 로컬 시드가 기본키 충돌로 죽는다.

```bash
docker exec -i perf-pg psql -U ondo -d ondo_wholesale -q -c "
DO \$\$ DECLARE r record; mx bigint; BEGIN
  FOR r IN SELECT c.relname AS seq, t.relname AS tbl, a.attname AS col
           FROM pg_class c
           JOIN pg_depend d ON d.objid = c.oid AND d.classid = 'pg_class'::regclass
           JOIN pg_class t ON t.oid = d.refobjid
           JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
           JOIN pg_namespace n ON n.oid = c.relnamespace
           WHERE c.relkind = 'S' AND n.nspname = 'wholesale' LOOP
    EXECUTE format('SELECT coalesce(max(%I), 0) FROM wholesale.%I', r.col, r.tbl) INTO mx;
    EXECUTE format('SELECT setval(''wholesale.%I'', %s, true)', r.seq, GREATEST(mx, 1));
  END LOOP; END \$\$;"
```

시드가 넣는 `password_hash` 는 `(perf)` 라 로그인이 안 된다. 폴링을 돌리려면
쓸 도매처들의 해시를 로그인 가능한 값으로 바꾼다 — dev 시드
(`wholesale-api/src/main/resources/db/seed/V901__seed_dev_login.sql`)의 해시를 그대로 쓰면 된다.
**해시도 비밀번호도 이 디렉터리에 적지 않는다.**

```bash
cd ../.. && SPRING_PROFILES_ACTIVE=local SERVER_PORT=8099 \
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55500/ondo_wholesale \
  SPRING_DATASOURCE_USERNAME=ondo SPRING_DATASOURCE_PASSWORD=ondo \
  SPRING_FLYWAY_ENABLED=false \
  java -jar wholesale-api/build/libs/wholesale-api-0.0.1-SNAPSHOT.jar

# 세션은 반드시 서로 다른 도매처로. 적게 두면 같은 요약만 읽어 재계산 부하가 사라진다
k6 run -e SHOPS=6000 -e SESSIONS=1000 -e DURATION=45s -e PASSWORD=... dashboard-poll.js

# 재계산을 끄고 비교하려면 앱에 ONDO_DASHBOARD_STALE_AFTER=1h
```
