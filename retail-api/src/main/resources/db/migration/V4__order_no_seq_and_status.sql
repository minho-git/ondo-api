-- ═══════════════════════════════════════════════════════════════
--  주문번호 채번 · 주문서 상태 (MUL-98)
--
--  소매 주문 접수를 실구현하면서 둘이 필요해졌다.
-- ═══════════════════════════════════════════════════════════════


-- ── ① 주문번호 채번 ──────────────────────────────────────────────
--
-- 화면에 찍히는 번호가 이 모양이다.
--
--   20260902-1420-0088
--   날짜      시각  그날의 연번
--
-- 도매 장끼가 쓰는 방식(D-076 · last_statement_date + last_statement_seq)과 같다.
-- 날짜를 같이 들고 있다가 날이 바뀌면 1 부터 다시 센다.
--
-- ⚠️ 소매처별이 아니라 전체 연번이다. order_group_no_uk 가 order_no 전체에
--    걸려 있어서, 소매처마다 1 부터 세면 두 소매처가 같은 분에 첫 주문을 넣을 때
--    번호가 통째로 겹친다.
--
-- 도매처럼 소매처 행에 컬럼을 붙이지 않고 표를 따로 둔 것도 그래서다 —
-- 채번 축이 소매처가 아니라 날짜다.
CREATE TABLE retail.order_no_seq (
    order_date  date        PRIMARY KEY,
    last_seq    int         NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE retail.order_no_seq IS '주문번호 뒤 네 자리. 날짜별 연번 (MUL-98)';


-- ── ② 주문서 상태 ───────────────────────────────────────────────
--
-- 접수는 도매처별로 갈린다. 넷 중 셋만 받아지는 게 정상이라 그건 성공이다.
-- 문제는 **전부 실패했을 때**다.
--
-- 도매를 부르려면 주문서 id 가 있어야 해서 주문서를 먼저 만든다. 그런데 도매가
-- 다 거절하면 주문이 하나도 안 달린 껍데기가 남는다.
--
-- 지우는 대신 상태로 남긴다. 이유가 둘이다.
--
--   1. 왜 실패했는지 나중에 볼 수 있다. 지우면 흔적이 없다
--   2. 멱등키(request_id)가 살아남는다. 지우면 같은 열쇠로 다시 눌렀을 때
--      주문서가 새로 생겨 id 가 바뀐다
--
-- 화면 계약("전부 안 되면 통합 주문을 안 만든다")은 그대로 지킨다 — 접수 API 는
-- 502 를 내고, 주문 내역은 ACCEPTED 만 보여준다. FAILED 는 사용자에게 안 보인다.
--
-- 도매처별 실패 사유까지 남기는 건 아직 안 한다(숙제 7번). 그게 있어야
-- 「같은 열쇠로 다시 오면 처음 결과 그대로」가 되는데, 저장소를 새로 만들
-- 값어치가 있는지 아직 모른다. 이 컬럼이 그 자리의 시작이다.
ALTER TABLE retail.order_group
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACCEPTED';

ALTER TABLE retail.order_group
    ADD CONSTRAINT order_group_status_ck CHECK (status IN ('ACCEPTED', 'FAILED'));

COMMENT ON COLUMN retail.order_group.status IS
    'ACCEPTED = 한 곳이라도 접수됨 · FAILED = 전부 거절됨. 내역에는 ACCEPTED 만 (MUL-98)';

-- 내역 조회가 상태로 거른다. 기존 인덱스는 상태를 안 봐서 하나 더 둔다.
CREATE INDEX order_group_accepted_idx
    ON retail.order_group (retailer_id, ordered_at DESC)
    WHERE status = 'ACCEPTED';
