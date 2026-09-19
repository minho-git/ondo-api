-- 대시보드 집계 요약 (MUL-135)
--
-- 대시보드는 열 때마다 주문·포장·출고·미송을 매번 센다. 30초마다 갱신되는 화면이라
-- 주문이 쌓일수록 같은 집계가 반복된다. 주문 300만 건(상가 1곳 1년치) 기준으로 재보니
-- 집계 6종 합계가 354ms, 인덱스를 걸어도 67ms 였다.
--
-- 그래서 숫자를 미리 세어 둔다. 갱신은 주기 재계산이고, 신선도는 최대 refreshed_at + 주기다.
-- 대시보드가 원래 30초 폴링이라 그 지연은 화면에서 구분되지 않는다.
--
-- ⚠️ 요약이 진실이 아니다. 진실은 언제나 원본 테이블이고, 요약은 다시 계산할 수 있어야 한다.
--    어긋나면 해당 도매처만 재계산한다.

-- ── 영업일 단위 — 오늘 주문 건수·금액·취소 ──────────────────
--
-- 영업일 경계는 자정이 아니라 KST 낮 12시다(BusinessDay). 12시 이전 주문은 전날 영업일에 속한다.
CREATE TABLE wholesale.dashboard_daily (
    wholesaler_id   bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    business_day    date        NOT NULL,
    order_count     int         NOT NULL DEFAULT 0,
    order_amount    bigint      NOT NULL DEFAULT 0,      -- 라인 스냅샷 합. 취소 포함(주문 칩 ALL 기준)
    cancelled_count int         NOT NULL DEFAULT 0,
    refreshed_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (wholesaler_id, business_day)
);

-- ── 도매처 단위 — 확정 대기 건수 ────────────────────────────
--
-- 개수와 "가장 오래 기다린 주문"을 한 쿼리로 구하면 LIMIT 1 인데도 전체를 센다
-- (count(*) over()). 개수만 떼어내면 나머지는 인덱스로 1건만 읽는다.
CREATE TABLE wholesale.dashboard_counter (
    wholesaler_id bigint      PRIMARY KEY REFERENCES wholesale.wholesaler (id),
    new_count     int         NOT NULL DEFAULT 0,
    refreshed_at  timestamptz NOT NULL DEFAULT now()
);

-- ── SKU 단위 — 남은 미송 ────────────────────────────────────
--
-- "입고일 지남·미등록"은 날짜가 바뀌면 저절로 변한다. 그래서 그 숫자는 세어 두지 않고
-- SKU 단위 잔여량만 들고, 날짜 판정은 읽을 때 variant 와 조인해서 한다.
CREATE TABLE wholesale.dashboard_backorder_sku (
    wholesaler_id     bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    variant_id        bigint      NOT NULL REFERENCES wholesale.variant (id),
    open_qty          int         NOT NULL DEFAULT 0,
    oldest_created_at timestamptz,
    refreshed_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (wholesaler_id, variant_id)
);

-- ── 소매처 단위 — 포장 대기 ─────────────────────────────────
--
-- 화면이 요구하는 것은 "대기 중인 소매처 수"다. 수를 세어 두는 대신 소매처를 행으로 두면
-- 세는 게 아니라 행을 읽는 일이 된다. 수령 방식별 집계도 같은 표에서 나온다.
CREATE TABLE wholesale.dashboard_packing_queue (
    wholesaler_id  bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    retailer_id    bigint      NOT NULL,                 -- 논리 참조(경계). FK 없음
    receive_method varchar(20) NOT NULL,
    qty            int         NOT NULL DEFAULT 0,
    refreshed_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (wholesaler_id, retailer_id, receive_method)
);

-- ── 원본 조회를 거드는 인덱스 ───────────────────────────────
--
-- 재계산과 대시보드의 나머지 조회가 쓰는 길. 외래키에는 인덱스가 자동으로 생기지 않는다.
CREATE INDEX order_item_order_idx     ON wholesale.order_item (order_id);
CREATE INDEX order_item_variant_idx   ON wholesale.order_item (variant_id);
CREATE INDEX packing_item_packing_idx ON wholesale.packing_item (packing_id) WHERE deleted_at IS NULL;
CREATE INDEX orders_new_idx           ON wholesale.orders (wholesaler_id, ordered_at, id) WHERE status = 'NEW';
CREATE INDEX orders_recent_idx        ON wholesale.orders (wholesaler_id, ordered_at);
CREATE INDEX outbound_open_idx        ON wholesale.outbound (wholesaler_id, created_at) WHERE shipped_at IS NULL;
CREATE INDEX outbound_shipped_idx     ON wholesale.outbound (wholesaler_id, shipped_at) WHERE shipped_at IS NOT NULL;
