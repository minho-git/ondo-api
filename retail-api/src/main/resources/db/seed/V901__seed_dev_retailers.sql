-- ═══════════════════════════════════════════════════════════════
--  개발 환경 시드 — 승인된 소매처 5곳 더 넣기
--
--  왜 필요한가
--    배포 dev 에 승인된 소매처가 봄봄상회 하나뿐이다. 주문·포장·출고 화면이
--    전부 한 소매처로만 차서, 소매처별로 갈리는 자리(포장 대기 소매처 수,
--    거래처 목록, 봉투 묶음)가 어떻게 보이는지 확인할 수가 없다.
--
--  왜 시드인가
--    승인 화면을 MVP 에서 뺐다(결정.md 「승인 절차 — 화면을 안 만든다」).
--    가입 API 로 만들면 PENDING 에 머물고, 승인시킬 API 가 없다. RDS 는
--    프라이빗 서브넷이라 밖에서 UPDATE 할 수도 없다 — V2 와 같은 길로 간다.
--
--  ⚠️ db/migration 이 아니라 db/seed 다. 운영(prod)은 이 폴더를 안 읽는다.
--  ⚠️ V2 를 고치지 않고 새 파일을 낸다. 적용된 파일은 체크섬이 걸린다.
--
--  비밀번호는 다른 시드 계정과 같은 ondo1234! (BCrypt).
--  개인정보는 평문이다 — 전부 지어낸 값이다.
--
--  도매 쪽은 건드릴 게 없다. 거래처(wholesale.partner)는 주문이 들어올 때
--  RetailGatewayOrderQuery.resolvePartnerId 가 만든다.
-- ═══════════════════════════════════════════════════════════════

INSERT INTO retail.retailer (email, password, shop_name, approval_status, approved_at)
VALUES
    ('byeolbit@ondo.test', '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
     '별빛의류',   'APPROVED', now()),
    ('hana@ondo.test',     '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
     '하나패션',   'APPROVED', now()),
    ('miso@ondo.test',     '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
     '미소상회',   'APPROVED', now()),
    ('jinju@ondo.test',    '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
     '진주의류',   'APPROVED', now()),
    ('onyu@ondo.test',     '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m',
     '온유패션',   'APPROVED', now());

INSERT INTO retail.retailer_private (retailer_id, owner_name, mobile, biz_reg_no)
SELECT id,
       CASE email
           WHEN 'byeolbit@ondo.test' THEN '최별빛'
           WHEN 'hana@ondo.test'     THEN '정하나'
           WHEN 'miso@ondo.test'     THEN '한미소'
           WHEN 'jinju@ondo.test'    THEN '오진주'
           WHEN 'onyu@ondo.test'     THEN '유온유'
       END,
       CASE email
           WHEN 'byeolbit@ondo.test' THEN '01022220001'
           WHEN 'hana@ondo.test'     THEN '01022220002'
           WHEN 'miso@ondo.test'     THEN '01022220003'
           WHEN 'jinju@ondo.test'    THEN '01022220004'
           WHEN 'onyu@ondo.test'     THEN '01022220005'
       END,
       CASE email
           WHEN 'byeolbit@ondo.test' THEN '2020200001'
           WHEN 'hana@ondo.test'     THEN '2020200002'
           WHEN 'miso@ondo.test'     THEN '2020200003'
           WHEN 'jinju@ondo.test'    THEN '2020200004'
           WHEN 'onyu@ondo.test'     THEN '2020200005'
       END
FROM retail.retailer
WHERE email IN ('byeolbit@ondo.test', 'hana@ondo.test', 'miso@ondo.test',
                'jinju@ondo.test', 'onyu@ondo.test');

INSERT INTO retail.terms_agreement (retailer_id, terms_type)
SELECT id, t.terms_type
FROM retail.retailer, (VALUES ('SERVICE'), ('PRIVACY')) AS t(terms_type)
WHERE email IN ('byeolbit@ondo.test', 'hana@ondo.test', 'miso@ondo.test',
                'jinju@ondo.test', 'onyu@ondo.test');

-- 파일 저장소를 아직 안 정했다(숙제). 경로만 가짜로 채운다 — V2 와 같다.
INSERT INTO retail.retailer_doc (retailer_id, doc_type, file_url, is_current)
SELECT id, 'BIZ_LICENSE', 'seed://dev/biz-license.png', true
FROM retail.retailer
WHERE email IN ('byeolbit@ondo.test', 'hana@ondo.test', 'miso@ondo.test',
                'jinju@ondo.test', 'onyu@ondo.test');

-- 다섯 곳이 다 승인 상태로 들어갔는지 본다. 조용히 빠지면 영상 찍다가 발견한다.
DO $$
DECLARE
    added int;
BEGIN
    SELECT count(*) INTO added
    FROM retail.retailer
    WHERE email IN ('byeolbit@ondo.test', 'hana@ondo.test', 'miso@ondo.test',
                    'jinju@ondo.test', 'onyu@ondo.test')
      AND approval_status = 'APPROVED';

    IF added <> 5 THEN
        RAISE EXCEPTION '승인 소매처 5곳 중 %곳만 들어갔다.', added;
    END IF;
END $$;
