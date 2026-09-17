-- 선수금 배분 (MUL-125).
--
-- 선수금은 입금 여러 건에서 남은 돈이 모인 것이다. 배분할 때 서버가 오래된 입금부터 남은 돈을
-- 골라 쓰므로, 거래처 입금을 입금 시각 순으로 읽는 일이 잦다 — 지금은 partner_id 인덱스가 없어
-- 거래처 하나를 보려 해도 입금 전체를 훑는다.
CREATE INDEX payment_partner_idx ON wholesale.payment (partner_id, paid_at, id);

-- 선수금으로 정산(POST /allocations) 멱등 — 입금(V10)과 같은 이유로 첫 응답 본문을 저장한다.
-- 응답의 prepaidRemaining 이 그 순간의 선수금이라 다시 계산하면 달라진다.
CREATE TABLE wholesale.allocation_idempotency (
    wholesaler_id    bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    idempotency_key  varchar(64) NOT NULL,
    request_hash     varchar(64) NOT NULL,
    response_body    text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT allocation_idempotency_pk PRIMARY KEY (wholesaler_id, idempotency_key)
);
