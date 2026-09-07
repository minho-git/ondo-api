-- 개발용 가짜 데이터. 실서비스 개시 전에 지운다.
--
-- 승인 거절 화면을 볼 계정이 없었다. GET /auth/me 가 rejection 을 내리는데
-- REJECTED 계정이 하나도 없으면 프론트가 그 화면을 그려볼 데가 없다.
--
-- V2 를 고치지 않고 새 파일로 둔다. 이미 적용된 마이그레이션을 고치면 체크섬이 깨진다.
--
-- 비밀번호는 다른 계정과 같은 ondo1234! (BCrypt)
-- 진짜 개인정보를 넣지 않는다 — 전부 지어낸 값이다.

INSERT INTO retail.retailer (email, password, shop_name, approval_status)
VALUES ('rejected@ondo.test',
        '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
        '반려상회', 'REJECTED');

INSERT INTO retail.retailer_private (retailer_id, owner_name, mobile, biz_reg_no)
SELECT id, '박반려', '01055556666', '5555566666'
FROM retail.retailer WHERE email = 'rejected@ondo.test';

INSERT INTO retail.terms_agreement (retailer_id, terms_type)
SELECT id, t.terms_type
FROM retail.retailer, (VALUES ('SERVICE'), ('PRIVACY')) AS t(terms_type)
WHERE email = 'rejected@ondo.test';

INSERT INTO retail.retailer_doc (retailer_id, doc_type, file_url, is_current)
SELECT id, 'BIZ_LICENSE', 'seed://dev/biz-license.png', true
FROM retail.retailer WHERE email = 'rejected@ondo.test';

-- 이력을 두 줄 넣는다. 한 줄만 넣으면 "마지막 줄만 읽는다" 가 맞는지 확인이 안 된다.
--
-- actor 에 운영자 이메일이 들어가는 게 정상이다. 응답에는 "운영자" 로 바꿔서 나간다 —
-- 소매처가 운영자 이메일을 알 이유가 없다.
INSERT INTO retail.approval_history (retailer_id, from_status, to_status, reason, actor, created_at)
SELECT id, NULL, 'PENDING', NULL, 'SYSTEM', now() - interval '2 day'
FROM retail.retailer WHERE email = 'rejected@ondo.test'
UNION ALL
SELECT id, 'PENDING', 'REJECTED',
       '제출하신 사업자등록증에서 상호를 확인할 수 없습니다. 선명한 사본으로 다시 신청해주세요.',
       'admin@ondo.test', now() - interval '1 day'
FROM retail.retailer WHERE email = 'rejected@ondo.test';
