-- 입금 등록 멱등 (MUL-124).
--
-- 입금은 중복 등록을 되돌릴 수 없는 요청이라 Idempotency-Key 가 필수다. 같은 키 재요청은
-- 첫 응답과 똑같은 본문을 내려야 하는데, 응답의 ledgerBalance 가 그 순간의 잔액이라
-- 다시 계산하면 달라진다 — 입고(V7 inbound_idempotency)와 같은 이유로 응답 본문을 저장한다.
-- request_hash 는 같은 키에 다른 본문을 보내는 실수(409 IDEMPOTENCY_KEY_REUSED)를 가려내는 지문이다.
CREATE TABLE wholesale.payment_idempotency (
    wholesaler_id    bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    idempotency_key  varchar(64) NOT NULL,
    request_hash     varchar(64) NOT NULL,
    payment_id       bigint      NOT NULL REFERENCES wholesale.payment (id),
    response_body    text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payment_idempotency_pk PRIMARY KEY (wholesaler_id, idempotency_key)
);
