-- 출고번호·장끼번호를 varchar → int 로 (MUL-49 · D-075 · D-076).
-- DTO 계약이 Integer 고 표시 코드(PKG-001 · JG-20260818-001) 조립은 프론트 몫이라
-- orders.order_number 전례를 따른다. 테이블이 비어 있어 USING 캐스트가 안전하다.
-- outbound_number_uk(도매처별 유니크)는 타입 변경 시 자동으로 다시 세워진다.

ALTER TABLE wholesale.outbound
    ALTER COLUMN outbound_number TYPE int USING outbound_number::int;

ALTER TABLE wholesale.outbound
    ALTER COLUMN statement_number TYPE int USING statement_number::int;
