-- 원장 줄이 가리키는 곳을 종류마다 못 박는다 (MUL-126).
--
-- 출고 줄은 어느 출고의 어느 주문인지, 입금 · 입금 취소 줄은 어느 입금인지가 있어야 뒤를 따라갈 수 있다.
-- 입금 줄은 주문을 가리키지 않는다 — 어느 주문 값인지는 배분(payment_allocation)이 적는다.
-- MUL-123 에서 미뤘던 제약이다(테스트 도우미가 옛 방식으로 원장을 넣고 있었다).
--
-- 이름을 target 으로 둔 건 부호 제약(sign_ck)보다 뒤에 검사되게 하려는 것이다 — PostgreSQL 은 CHECK 를
-- 이름 순으로 보므로 둘 다 어긴 행은 부호 위반으로 보고된다.
ALTER TABLE wholesale.receivable_ledger
    ADD CONSTRAINT receivable_ledger_target_ck CHECK (
        (entry_type = 'OUTBOUND'
            AND order_id IS NOT NULL AND outbound_id IS NOT NULL AND payment_id IS NULL) OR
        (entry_type IN ('PAYMENT', 'PAYMENT_VOID')
            AND payment_id IS NOT NULL AND order_id IS NULL AND outbound_id IS NULL) OR
        (entry_type = 'ADJUST' AND memo IS NOT NULL)
    );
