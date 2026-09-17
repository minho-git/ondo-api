-- 정산 원장 기반 (MUL-123).
--
-- 미수 원장은 거래처와의 돈 흐름만 적고, "이 돈이 어느 주문 값인가"는 배분이 따로 적는다.
-- 입금 한 건을 주문 여럿에 나누거나 선수금으로 남기려면 원장 행이 주문에 묶여 있으면 안 된다.


-- 배분 — 입금을 주문에 붙인 기록
--   배분은 출고된 금액에만 한다. 먼저 받은 돈은 선수금으로 들고 있다가 출고 뒤 붙인다.
--   선수금에서 배분할 때도 서버가 오래된 입금부터 남은 돈을 골라 payment_id 를 채운다 —
--   입금을 취소하면 그 입금의 배분만 계산에서 빠져야 해서다.
--   (payment_id, order_id) 유니크는 두지 않는다. 같은 입금을 같은 주문에 나눠 붙일 수 있고,
--   넘치게 붙이는 건 금액 상한(입금 금액 · 출고금액 − 기존 배분)이 막는다.
--   취소는 행을 지우지 않고 cancelled_at 만 찍는다. 계산은 cancelled_at IS NULL 인 행만 센다.

CREATE TABLE wholesale.payment_allocation (
    id            bigserial   PRIMARY KEY,
    payment_id    bigint      NOT NULL REFERENCES wholesale.payment (id),
    order_id      bigint      NOT NULL REFERENCES wholesale.orders (id),
    amount        bigint      NOT NULL,
    cancelled_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payment_allocation_amount_ck CHECK (amount > 0)
);

-- 주문별 받은 돈 · 입금별 남은 돈을 셀 때의 두 축
CREATE INDEX payment_allocation_order_idx   ON wholesale.payment_allocation (order_id);
CREATE INDEX payment_allocation_payment_idx ON wholesale.payment_allocation (payment_id);


-- 원장 — 입금 줄은 주문을 가리키지 않는다
ALTER TABLE wholesale.receivable_ledger
    ALTER COLUMN order_id DROP NOT NULL;


-- 원장 종류에 입금 취소를 더하고, 종류마다 부호를 못 박는다
--   delta 플러스 = 소매처가 갚을 돈이 늘었다. 방향을 부호로만 표시하므로 틀린 부호는 DB 가 거절한다.
--   도매 API 는 화면 계약(판매 −, 입금 +)에 맞춰 내보낼 때 뒤집는다 — 저장은 이쪽이 기준이다.
ALTER TABLE wholesale.receivable_ledger
    DROP CONSTRAINT receivable_ledger_type_ck;

ALTER TABLE wholesale.receivable_ledger
    ADD CONSTRAINT receivable_ledger_type_ck
        CHECK (entry_type IN ('OUTBOUND', 'PAYMENT', 'PAYMENT_VOID', 'ADJUST'));

ALTER TABLE wholesale.receivable_ledger
    ADD CONSTRAINT receivable_ledger_sign_ck CHECK (
        (entry_type = 'OUTBOUND'     AND delta > 0) OR
        (entry_type = 'PAYMENT'      AND delta < 0) OR
        (entry_type = 'PAYMENT_VOID' AND delta > 0) OR
        (entry_type = 'ADJUST'       AND delta <> 0)
    );


-- 거래처 미수 칸을 원장과 맞춘다
--   MUL-49 부터 출고 확정이 원장을 썼지만 이 칸은 갱신하지 않아 0 으로 남아 있다.
--   이제부터 원장 쓰기가 같은 트랜잭션에서 같이 고친다.
UPDATE wholesale.partner p
SET receivable_balance = l.balance
FROM (SELECT partner_id, sum(delta) AS balance
      FROM wholesale.receivable_ledger
      GROUP BY partner_id) l
WHERE l.partner_id = p.id;
