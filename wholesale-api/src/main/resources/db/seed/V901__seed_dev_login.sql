-- ═══════════════════════════════════════════════════════════════
--  개발 환경 시드 — 도매처 로그인 열기 (MUL-103)
--
--  V900 이 만든 도매처 3곳은 password_hash 자리에 '(로그인 불가 · 시드)' 라는
--  글자가 박혀 있다. BCrypt 가 아니라 어떤 비밀번호를 넣어도 대조에 실패한다.
--
--  그때는 "상품의 주인 노릇만 하면 된다" 고 봤다. 그런데 창은이가 도매 화면
--  (pos.ddmondo.co.kr)을 배포 API 로 붙이려면 로그인이 돼야 한다.
--
--  배포에 다른 계정이 없다 — LocalDevAccountSeeder 는 @Profile("local") 이고,
--  RDS 는 프라이빗 서브넷이라 밖에서 직접 넣을 수도 없다. 이 시드가 유일한 길이다.
--
--  ⚠️ V900 을 고치지 않고 새 파일을 낸다. V900 은 배포 DB 에 이미 적용됐고,
--     Flyway 는 적용된 파일의 체크섬이 바뀌면 기동을 막는다.
--
--  ⚠️ db/migration 이 아니라 db/seed 다. 운영(prod)은 이 폴더를 안 읽으므로
--     로그인되는 시드 계정이 운영에 생기지 않는다. 그게 이 티켓의 목적이다.
-- ═══════════════════════════════════════════════════════════════

-- 비밀번호는 ondo1234! (BCrypt). 소매 시드(V2)와 같은 값으로 맞춘다 —
-- 창은이가 도매·소매를 오가며 개발하는데 비밀번호가 다르면 헷갈린다.
--
-- 도매 비밀번호 정책(SignupService.PASSWORD_POLICY)도 통과하는 값이다.
-- 가입 API 로 만든 계정과 같은 모양이어야 나중에 정책을 바꿀 때 같이 걸린다.
UPDATE wholesale.wholesaler
SET password_hash = '$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m'
WHERE id IN (101, 102, 103);

-- 셋 다 안 바뀌었으면 V900 과 어긋난 것이다. 조용히 넘어가면 나중에
-- "왜 로그인이 안 되지" 로 돌아온다.
DO $$
DECLARE
    opened int;
BEGIN
    SELECT count(*) INTO opened
    FROM wholesale.wholesaler
    WHERE id IN (101, 102, 103)
      AND password_hash LIKE '$2a$%';

    IF opened <> 3 THEN
        RAISE EXCEPTION '도매 시드 계정 3곳 중 %곳만 열렸다. V900 의 도매처 id 를 확인해라.', opened;
    END IF;
END $$;
