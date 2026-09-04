-- 소매 상호(retailer_name)처럼 전화번호도 주문 시점 스냅샷으로 둔다 (MUL-47).
-- 채우는 쪽은 소매 주문 접수 API — 값이 들어오기 전까지 주문 상세에는 NULL 로 내려간다.
ALTER TABLE wholesale.partner ADD COLUMN retailer_phone varchar(20);
