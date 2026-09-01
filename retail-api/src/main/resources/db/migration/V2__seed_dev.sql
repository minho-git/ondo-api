-- 개발용 가짜 데이터. 실서비스 개시 전에 지운다.
--
-- 승인 화면을 MVP 에서 뺐다(결정.md 「승인 절차 — 화면을 안 만든다」).
-- 새로 가입하면 PENDING 에 머물러 아무도 못 들어가므로, 여기서 APPROVED 계정을 박고 그걸로 개발한다.
--
-- 비밀번호는 둘 다 ondo1234! (BCrypt)
-- 개인정보는 평문이다. 암호화는 실서비스 개시 전에 한 번에 붙인다(결정.md).
-- 진짜 개인정보를 넣지 않는다 — 전부 지어낸 값이다.

-- 개발 주 계정. 이걸로 로그인해서 만든다.
INSERT INTO retail.retailer (email, password, shop_name, approval_status, approved_at)
VALUES ('bombom@ondo.test',
        '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
        '봄봄상회', 'APPROVED', now());

-- 승인 대기 화면과 403 ACCOUNT_NOT_APPROVED 를 확인하려고 둔다.
INSERT INTO retail.retailer (email, password, shop_name, approval_status)
VALUES ('pending@ondo.test',
        '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
        '대기상회', 'PENDING');

INSERT INTO retail.retailer_private (retailer_id, owner_name, mobile, biz_reg_no)
SELECT id, '김봄', '01012345678', '1234567890' FROM retail.retailer WHERE email = 'bombom@ondo.test'
UNION ALL
SELECT id, '이대기', '01098765432', '9876543210' FROM retail.retailer WHERE email = 'pending@ondo.test';

INSERT INTO retail.terms_agreement (retailer_id, terms_type)
SELECT id, t.terms_type
FROM retail.retailer, (VALUES ('SERVICE'), ('PRIVACY')) AS t(terms_type)
WHERE email IN ('bombom@ondo.test', 'pending@ondo.test');

-- 파일 저장소를 아직 안 정했다(숙제). 경로만 가짜로 채운다.
INSERT INTO retail.retailer_doc (retailer_id, doc_type, file_url, is_current)
SELECT id, 'BIZ_LICENSE', 'seed://dev/biz-license.png', true
FROM retail.retailer WHERE email IN ('bombom@ondo.test', 'pending@ondo.test');
