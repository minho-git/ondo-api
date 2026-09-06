-- ═══════════════════════════════════════════════════════════════
--  개발 환경 시드 — 상품 · 주문 · 미송 (MUL-110)
--
--  ⚠️ 개발 환경 전용이다. 운영 띄우기 전에 지우는 마이그레이션을 낸다 — MUL-103.
--     소매의 V2·V3(시드 계정)와 같이 처리한다.
--
--  왜 마이그레이션인가
--    창은이가 배포 API 로 화면을 붙이는데 도매 DB 에 상품이 0건이라 응답이
--    전부 빈 배열이다. 상품을 넣으려면 도매 상품 등록 API 를 불러야 하고
--    그러려면 도매 계정이 필요한데, 시드 계정은 @Profile("local") 이라 배포에 없다.
--    RDS 는 프라이빗 서브넷이고 인터넷으로 가는 길도 없어서 밖에서 못 붙는다.
--
--    남은 길이 이거였다. 앱이 뜰 때 Flyway 가 넣는다 — 인프라를 안 건드린다.
--
--  ⚠️ db/migration 이 아니라 db/seed 다. 마이그레이션 폴더에 두면 Testcontainers 가
--     테스트에서도 다 돌려서 도매 테스트 40개가 깨진다 — 시드가 넣는 도매처와
--     테스트가 넣는 도매처의 이메일이 부딪힌다.
--
--     배포 태스크에만 SPRING_FLYWAY_LOCATIONS 로 이 폴더를 더해준다(infra/ecs.tf).
--     끄는 것도 그 줄을 지우면 끝이다. 채빈 영역인 db/migration 은 안 건드린다.
--
--     번호를 900 으로 크게 잡은 것도 같은 이유다. 채빈이 V7·V8 을 쓸 자리를 비워둔다.
--
--  카테고리·색상은 V5 가 이미 넣었다. 그 위에 상품만 얹는다.
--
--  ⚠️ 소매 V900 과 짝이다. 도매 orders.retail_order_id 가 소매 order_group.id 를
--     가리키는데 DB 가 갈라져 있어 FK 가 없다. 한쪽만 나가면 미송 목록에
--     주문번호가 안 붙고 소매 로그에 경고가 뜬다.
--
--  ⚠️ 소매처 id 1·2 를 박아 넣는다. 소매 V2·V3 가 만드는 봄봄상회·대기상회다.
--     소매 V4 가 그 값을 확인한다.
--
--  ⚠️ 맨 아래에서 시퀀스를 올린다. id 를 직접 박으면 bigserial 카운터가 0 에
--     머물러서, 채빈이 상품을 추가하는 순간 PK 부터 충돌한다.
--     같은 이유로 wholesaler.last_product_seq · last_order_seq 도 맞춘다.
-- ═══════════════════════════════════════════════════════════════
--  도매처 3곳
--
--  password_hash 는 BCrypt 가 아니다. 이 계정들은 상품의 주인 노릇만 하고
--  로그인은 안 된다 — 소매가 읽을 데이터를 만드는 게 목적이다.
--  도매 화면 로그인은 채빈의 dev@ondo.test / Ondo!2345 를 쓴다.
--
--  last_product_seq 는 아래에서 넣는 상품 수와 맞춘다. 도매처당 2개씩이다.
-- ═══════════════════════════════════════════════════════════════

INSERT INTO wholesale.wholesaler
    (id, email, password_hash, phone, biz_reg_no, biz_name, biz_owner_name,
     store_phone, store_building, store_unit, biz_category,
     approval_status, approved_at, last_product_seq)
VALUES
    (101, 'moodon@ondo.test',     '(로그인 불가 · 시드)', '01011110001', '1010100001',
     '무드온',     '김무드', '0221110001', '청평화패션몰', '2층 24호',  '여성의류',
     'APPROVED', now(), 2),
    (102, 'raon@ondo.test',       '(로그인 불가 · 시드)', '01011110002', '1010100002',
     '라온',       '이라온', '0221110002', '디오트',       '3층 B-12',  '여성의류',
     'APPROVED', now(), 2),
    (103, 'cottonclub@ondo.test', '(로그인 불가 · 시드)', '01011110003', '1010100003',
     '코튼클럽',   '박코튼', '0221110003', 'APM',          '5층 501호', '여성의류',
     'APPROVED', now(), 2);


-- ═══════════════════════════════════════════════════════════════
--  상품 6건
--
--  id 규칙 — 상품 10xx · 게시글 20xx · 옵션 30xx · 색상옵션 40xx · 이미지 50xx
--  끝 두 자리를 맞춰뒀다. 상품 1001 의 게시글은 2001 이다.
--
--  카테고리는 V5 의 리프(depth 3)를 쓴다 (D-058 — 상품은 리프에만 단다)
--    132 여성>블라우스/셔츠>셔츠      152 여성>니트>니트 가디건
--    162 여성>팬츠>데님              121 여성>상의>티셔츠
--    112 여성>아우터>재킷            142 여성>원피스>롱
--
--  product_number 는 도매처별 연번이다. 도매처마다 1 부터 다시 센다.
--  variant_seq 도 상품별 연번이다. 둘 다 UNIQUE 라 겹치면 INSERT 가 실패한다.
-- ═══════════════════════════════════════════════════════════════

INSERT INTO wholesale.product (id, wholesaler_id, product_number, name, category_id, last_variant_seq) VALUES
    (1001, 101, 1, '빈티지 플라워 셔츠',  132, 5),
    (1002, 101, 2, '루즈핏 니트 가디건',  152, 2),
    (1003, 102, 1, '와이드 데님 팬츠',    162, 4),
    (1004, 102, 2, '코튼 반팔 티셔츠',    121, 6),
    (1005, 103, 1, '린넨 셋업 자켓',      112, 2),
    (1006, 103, 2, '시즌 종료 원피스',    142, 1);

-- 색상은 V5 마스터의 id 를 쓴다
--   1 블랙 · 4 화이트 · 7 베이지 · 11 네이비 · 13 소라 · 17 레드
-- 마스터에 26색이 있고 여기서 6색만 쓴다. 나머지는 상품이 없는 색이라,
-- 소매 필터에서 그런 색을 골랐을 때 목록이 0건으로 정상 동작하는지 볼 수 있다.
-- 색상옵션에 이미지를 안 넣는다. 도매 상품 등록이 옵션별 이미지를 안 받기로 정해져서
-- (창은·채빈 합의) 진짜 데이터에는 이 값이 없다. 시드가 채우면 되는 것처럼 착각하게 된다
INSERT INTO wholesale.color_option (id, product_id, color_id) VALUES
    (4001, 1001, 17),
    (4002, 1001, 11),
    (4003, 1002, 1),
    (4004, 1002, 7),
    (4005, 1003, 11),
    (4006, 1003, 13),
    (4007, 1004, 4),
    (4008, 1004, 1),
    (4009, 1005, 7),
    (4010, 1006, 1);

INSERT INTO wholesale.variant (id, color_option_id, product_id, size, variant_seq, stock_qty) VALUES
    -- 1001 빈티지 플라워 셔츠 · 레드 3 + 네이비 2
    (3001, 4001, 1001, 'S',    1, 40),
    (3002, 4001, 1001, 'M',    2, 35),
    (3003, 4001, 1001, 'L',    3,  0),   -- 재고 0. 접수는 되고 미송으로 잡힌다
    (3004, 4002, 1001, 'S',    4, 20),
    (3005, 4002, 1001, 'M',    5, 18),
    -- 1002 루즈핏 니트 가디건 · 낱장 상품이라 FREE 만
    (3006, 4003, 1002, 'FREE', 1, 12),
    (3007, 4004, 1002, 'FREE', 2,  8),
    -- 1003 와이드 데님 팬츠
    (3008, 4005, 1003, 'S',    1, 15),
    (3009, 4005, 1003, 'M',    2, 22),
    (3010, 4005, 1003, 'L',    3,  9),
    (3011, 4006, 1003, 'M',    4,  6),
    -- 1004 코튼 반팔 티셔츠 · 사이즈가 제일 많다
    (3012, 4007, 1004, 'S',    1, 50),
    (3013, 4007, 1004, 'M',    2, 60),
    (3014, 4007, 1004, 'L',    3, 45),
    (3015, 4007, 1004, 'XL',   4, 10),
    (3016, 4008, 1004, 'M',    5, 30),
    (3017, 4008, 1004, 'L',    6, 25),
    -- 1005 린넨 셋업 자켓
    (3018, 4009, 1005, 'M',    1,  7),
    (3019, 4009, 1005, 'L',    2,  4),
    -- 1006 시즌 종료 원피스
    (3020, 4010, 1006, 'M',    1,  3);


-- ═══════════════════════════════════════════════════════════════
--  게시글
--
--  2006 만 SEASON_ENDED 다. 나머지는 ON_SALE.
--  소매 목록·검색에는 ON_SALE 만 나와야 하고, 2006 은 안 나와야 한다.
--  장바구니에 담아둔 사이 도매가 시즌을 닫은 경우를 프론트가 회색으로 그려봐야 해서
--  일부러 하나 남겨둔다 — 옵션 조회로는 잡히되 주문은 안 되는 상태다.
-- ═══════════════════════════════════════════════════════════════

INSERT INTO wholesale.listing
    (id, product_id, title, description, single_piece_allowed, status, season_started_at, season_ended_at)
VALUES
    (2001, 1001, '빈티지 플라워 셔츠', '봄 신상. 부드러운 레이온 혼방.',
     false, 'ON_SALE',      now() - interval '10 day', NULL),
    (2002, 1002, '루즈핏 니트 가디건', '오버핏. 낱장 구매 가능.',
     true,  'ON_SALE',      now() - interval '8 day',  NULL),
    (2003, 1003, '와이드 데님 팬츠',   '워싱 데님. 밑단 마감 처리.',
     false, 'ON_SALE',      now() - interval '6 day',  NULL),
    (2004, 1004, '코튼 반팔 티셔츠',   '20수 코튼. 낱장 구매 가능.',
     true,  'ON_SALE',      now() - interval '4 day',  NULL),
    (2005, 1005, '린넨 셋업 자켓',     '린넨 혼방 셋업. 팬츠 별도.',
     false, 'ON_SALE',      now() - interval '2 day',  NULL),
    (2006, 1006, '시즌 종료 원피스',   '지난 시즌 상품.',
     false, 'SEASON_ENDED', now() - interval '90 day', now() - interval '30 day');

-- 판매가는 여기에만 있다. variant 에는 없다.
-- order_limit 0 = 무제한 (D-057)
INSERT INTO wholesale.listing_variant (listing_id, variant_id, sale_price, order_limit) VALUES
    (2001, 3001, 12500, 500),
    (2001, 3002, 12500, 500),
    (2001, 3003, 13500,   0),   -- 사이즈마다 값이 다를 수 있다. L 만 1000원 비싸다
    (2001, 3004, 12500, 500),
    (2001, 3005, 12500, 500),
    (2002, 3006, 23000,   0),
    (2002, 3007, 23000,   0),
    (2003, 3008, 31000,   0),
    (2003, 3009, 31000,   0),
    (2003, 3010, 31000,   0),
    (2003, 3011, 31000,   0),
    (2004, 3012,  8900,   0),
    (2004, 3013,  8900,   0),
    (2004, 3014,  8900,   0),
    (2004, 3015,  9900,   0),   -- XL 만 비싸다
    (2004, 3016,  8900,   0),
    (2004, 3017,  8900,   0),
    (2005, 3018, 45000,  20),
    (2005, 3019, 45000,  20),
    (2006, 3020, 19000,   0);

-- sort_order 0 이 대표 이미지다. 목록 카드의 썸네일이 이걸 쓴다.
INSERT INTO wholesale.listing_image (id, listing_id, url, sort_order) VALUES
    (5001, 2001, 'https://cdn.ondo.test/listings/2001/1.jpg', 0),
    (5002, 2001, 'https://cdn.ondo.test/listings/2001/2.jpg', 1),
    (5003, 2002, 'https://cdn.ondo.test/listings/2002/1.jpg', 0),
    (5004, 2003, 'https://cdn.ondo.test/listings/2003/1.jpg', 0),
    (5005, 2003, 'https://cdn.ondo.test/listings/2003/2.jpg', 1),
    (5006, 2004, 'https://cdn.ondo.test/listings/2004/1.jpg', 0),
    (5007, 2005, 'https://cdn.ondo.test/listings/2005/1.jpg', 0),
    (5008, 2006, 'https://cdn.ondo.test/listings/2006/1.jpg', 0);


-- ═══════════════════════════════════════════════════════════════
--  미송 (MUL-97)
--
--  미송은 주문에서 나온다. 그런데 주문 접수 API 가 아직 스텁이라(채빈 MUL-47)
--  미송 행을 만드는 코드가 어디에도 없다. 그래서 여기서 손으로 심는다.
--
--  ⚠ 소매 시드와 짝이 맞아야 한다. 도매 orders.retail_order_id 가 소매
--    order_group.id 를 가리키는데 FK 가 없다(DB 가 갈라져 있다). 한쪽만 돌리면
--    미송 목록에 주문번호가 안 붙고 소매 로그에 경고가 뜬다.
--      docs-local/시드-소매-로컬.sql 을 같이 돌려라.
--
--  소매처 id 도 하드코딩이다. 소매 V2 시드의 봄봄상회가 1, 대기상회가 2 다.
--  소매 시드 파일이 이 값을 확인한다.
-- ═══════════════════════════════════════════════════════════════

-- 거래처 — 도매처와 소매처의 관계. 미송을 소매처별로 자르는 축이다
INSERT INTO wholesale.partner (id, wholesaler_id, retailer_id, retailer_name, trade_type) VALUES
    (6001, 101, 1, '봄봄상회', 'NORMAL'),
    (6002, 102, 1, '봄봄상회', 'NORMAL'),
    (6003, 103, 1, '봄봄상회', 'NORMAL'),
    (6004, 101, 2, '대기상회', 'NORMAL');   -- 남의 소매처. 봄봄상회 응답에 섞이면 안 된다

-- 주문
--   order_number 는 도매처별 연번이다. 101 은 셋(1·2·3), 102·103 은 하나씩.
--   retail_order_id 는 소매 order_group.id 다. 소매 시드의 6001~6005 와 짝이다.
INSERT INTO wholesale.orders
    (id, order_number, retail_order_id, partner_id, wholesaler_id, status,
     payment_term, receive_method, agent_name, agent_phone, ordered_at)
VALUES
    (7001, 1, 6001, 6001, 101, 'CONFIRMED', 'CASH',          'AGENT',    '박삼촌', '01033330001', now() - interval '3 day'),
    (7002, 1, 6002, 6002, 102, 'CONFIRMED', 'BANK_TRANSFER', 'RETAILER', NULL,     NULL,          now() - interval '1 day'),
    (7003, 1, 6003, 6003, 103, 'NEW',       'CASH',          'AGENT',    '박삼촌', '01033330001', now() - interval '2 hour'),
    (7004, 2, 6004, 6001, 101, 'CONFIRMED', 'CASH',          'AGENT',    '박삼촌', '01033330001', now() - interval '5 day'),
    (7005, 3, 6005, 6004, 101, 'CONFIRMED', 'CASH',          'RETAILER', NULL,     NULL,          now() - interval '2 day');

-- 주문 항목
--   unit_price 는 주문 시점 판매가 스냅샷이라 listing_variant 와 같은 값을 넣는다.
--   allocated_qty 가 qty 보다 작은 만큼이 미송이다.
INSERT INTO wholesale.order_item (id, order_id, variant_id, qty, unit_price, allocated_qty, shipped_qty) VALUES
    (8001, 7001, 3003, 4, 13500, 0, 0),   -- 빈티지 셔츠 레드 L. 재고 0 이라 통째로 미송
    (8002, 7002, 3011, 1, 31000, 0, 0),   -- 와이드 데님 팬츠
    (8003, 7003, 3019, 2, 45000, 0, 0),   -- 린넨 셋업 자켓 L
    (8004, 7004, 3002, 3, 12500, 3, 3),   -- 다 받았다. 미송이 해소된 줄
    (8005, 7005, 3003, 9, 13500, 0, 0);   -- 남의 소매처 것

-- 미송
--   created_at 을 주문 시각에 맞춘다. 기본값 now() 로 두면 5일 전 주문의 미송이
--   방금 생긴 것으로 보여서 도매 화면의 경과일이 전부 0 일이 된다.
INSERT INTO wholesale.backorder (id, order_item_id, qty, status, created_at) VALUES
    (9001, 8001, 4, 'OPEN',     now() - interval '3 day'),
    (9002, 8002, 1, 'OPEN',     now() - interval '1 day'),
    (9003, 8003, 2, 'OPEN',     now() - interval '2 hour'),
    (9004, 8004, 3, 'RESOLVED', now() - interval '5 day'),   -- 소매 목록에 안 나와야 한다
    (9005, 8005, 9, 'OPEN',     now() - interval '2 day');   -- 남의 것. 안 나와야 한다

-- 예상 입고일은 SKU 에 붙는다(D-067). 미송이 아니라 variant 가 들고 있다.
--   3011 은 일부러 비워둔다 — 화면이 "도매처가 입고일을 안내할 예정이에요" 를
--   그리는 분기를 봐야 한다.
UPDATE wholesale.variant SET expected_inbound_date   = (now() + interval '2 day')::date,
                             expected_inbound_reason = '공장 재입고 예정'
 WHERE id = 3003;
UPDATE wholesale.variant SET expected_inbound_date   = (now() + interval '5 day')::date,
                             expected_inbound_reason = '원단 수급 지연'
 WHERE id = 3019;

-- 주문번호 채번도 맞춘다. 안 맞추면 채빈이 주문을 넣을 때 orders_number_uk 에 걸린다.
UPDATE wholesale.wholesaler SET last_order_seq = 3 WHERE id = 101;
UPDATE wholesale.wholesaler SET last_order_seq = 1 WHERE id IN (102, 103);


-- ═══════════════════════════════════════════════════════════════
--  시퀀스 맞추기
--
--  여기를 빠뜨리면 채빈이 상품을 하나 추가하는 순간 id=1 을 쓰려다 죽는다.
--  common 쪽은 V5 가 이미 맞춰뒀으므로 건드리지 않는다.
--  max(id) 로 올리므로 이 파일을 다시 돌려도 안전하다.
-- ═══════════════════════════════════════════════════════════════

SELECT setval(pg_get_serial_sequence('wholesale.wholesaler',    'id'), (SELECT max(id) FROM wholesale.wholesaler));
SELECT setval(pg_get_serial_sequence('wholesale.product',       'id'), (SELECT max(id) FROM wholesale.product));
SELECT setval(pg_get_serial_sequence('wholesale.color_option',  'id'), (SELECT max(id) FROM wholesale.color_option));
SELECT setval(pg_get_serial_sequence('wholesale.variant',       'id'), (SELECT max(id) FROM wholesale.variant));
SELECT setval(pg_get_serial_sequence('wholesale.listing',       'id'), (SELECT max(id) FROM wholesale.listing));
SELECT setval(pg_get_serial_sequence('wholesale.listing_image', 'id'), (SELECT max(id) FROM wholesale.listing_image));
SELECT setval(pg_get_serial_sequence('wholesale.partner',      'id'), (SELECT max(id) FROM wholesale.partner));
SELECT setval(pg_get_serial_sequence('wholesale.orders',       'id'), (SELECT max(id) FROM wholesale.orders));
SELECT setval(pg_get_serial_sequence('wholesale.order_item',   'id'), (SELECT max(id) FROM wholesale.order_item));
SELECT setval(pg_get_serial_sequence('wholesale.backorder',    'id'), (SELECT max(id) FROM wholesale.backorder));
