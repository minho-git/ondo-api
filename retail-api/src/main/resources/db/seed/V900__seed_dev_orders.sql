-- ═══════════════════════════════════════════════════════════════
--  개발 환경 시드 — 통합 주문서 (MUL-110)
--
--  ⚠️ 개발 환경 전용이다. 운영 띄우기 전에 지우는 마이그레이션을 낸다 — MUL-103.
--     V2·V3(시드 계정)와 같이 처리한다.
--
--  왜 필요한가
--    미송 목록에 찍히는 주문번호(20260830-0930-0085)는 소매 것이다. 도매에는 없다 —
--    도매의 orders.order_number 는 도매처별 연번이라 아예 다른 번호다.
--    도매가 retailOrderId 까지 주고 소매가 이 표에서 번호를 채운다 (MUL-97).
--
--    주문 접수는 아직 목이라(MUL-98) order_group 에 행을 만드는 코드가 없다.
--
--  ⚠️ db/migration 이 아니라 db/seed 다. 배포 태스크에만 SPRING_FLYWAY_LOCATIONS 로
--     더해준다(infra/ecs.tf). 마이그레이션 폴더에 두면 테스트에도 들어간다.
--
--  ⚠️ 도매 V900 과 짝이다. 한쪽만 나가면 미송 목록의 orderNo 가 비고
--     소매 로그에 "미송에 걸린 주문서를 소매에서 못 찾았다" 경고가 뜬다.
-- ═══════════════════════════════════════════════════════════════

-- ── 소매처 id 를 확인한다 ────────────────────────────────────────
-- 도매 V900 이 partner.retailer_id 에 1·2 를 박아 넣는다. DB 가 갈라져 있어 FK 가
-- 없으니, 어긋나면 조용히 "미송 0건" 이 된다. 여기서 시끄럽게 막는다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM retail.retailer WHERE id = 1 AND email = 'bombom@ondo.test') THEN
        RAISE EXCEPTION '봄봄상회가 id 1 이 아니다. 도매 V900 의 partner.retailer_id 와 안 맞는다.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM retail.retailer WHERE id = 2 AND email = 'pending@ondo.test') THEN
        RAISE EXCEPTION '대기상회가 id 2 가 아니다. 도매 V900 의 partner 6004 와 안 맞는다.';
    END IF;
END $$;


-- ═══════════════════════════════════════════════════════════════
--  통합 주문서 5건
--
--  소매는 도매처가 여럿이어도 주문서를 하나로 받고, 접수할 때 도매처별로 쪼갠다.
--  여기 시드는 주문서 하나에 도매처 하나씩이라 도매 orders 와 1:1 이다.
--
--  order_no 는 ordered_at 에서 만든다. 고정 문자열로 박으면 며칠 뒤엔
--  주문 시각과 번호의 날짜가 서로 안 맞는다.
--  뒤 네 자리는 그날의 연번인데, 시드는 채번기를 안 돌리므로 손으로 준다.
--
--  total_amount 는 unit_price × qty 다. 도매 V900 의 주문 항목과 맞춰뒀다.
--    6001  13500 × 4 = 54000     6002  31000 × 1 = 31000
--    6003  45000 × 2 = 90000     6004  12500 × 3 = 37500
--    6005  13500 × 9 = 121500
-- ═══════════════════════════════════════════════════════════════

INSERT INTO retail.order_group
    (id, retailer_id, request_id, order_no, agent_name, agent_phone, total_amount, ordered_at)
VALUES
    (6001, 1, 'seed-mul110-6001',
     to_char(now() - interval '3 day',  'YYYYMMDD-HH24MI') || '-0085',
     '박삼촌', '01033330001',  54000, now() - interval '3 day'),

    (6002, 1, 'seed-mul110-6002',
     to_char(now() - interval '1 day',  'YYYYMMDD-HH24MI') || '-0087',
     NULL,     NULL,           31000, now() - interval '1 day'),

    (6003, 1, 'seed-mul110-6003',
     to_char(now() - interval '2 hour', 'YYYYMMDD-HH24MI') || '-0088',
     '박삼촌', '01033330001',  90000, now() - interval '2 hour'),

    -- 미송이 이미 해소된 주문. 미송 목록에는 안 나오고 주문 내역에만 남는다
    (6004, 1, 'seed-mul110-6004',
     to_char(now() - interval '5 day',  'YYYYMMDD-HH24MI') || '-0071',
     '박삼촌', '01033330001',  37500, now() - interval '5 day'),

    -- 대기상회 것. 봄봄상회로 로그인하면 안 보여야 한다
    (6005, 2, 'seed-mul110-6005',
     to_char(now() - interval '2 day',  'YYYYMMDD-HH24MI') || '-0079',
     NULL,     NULL,          121500, now() - interval '2 day');


-- ═══════════════════════════════════════════════════════════════
--  시퀀스 맞추기
--
--  id 를 직접 박았으니 bigserial 카운터를 올려둔다. 안 그러면 주문 실 연동(MUL-98)이
--  붙는 순간 id=1 을 쓰려다 PK 충돌로 죽는다.
-- ═══════════════════════════════════════════════════════════════

SELECT setval(pg_get_serial_sequence('retail.order_group', 'id'), (SELECT max(id) FROM retail.order_group));
