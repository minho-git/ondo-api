-- ═══════════════════════════════════════════════════════════════
--  MUL-67 · 회원 심사 이력
--
--  왜   지금까지 심사 결과는 wholesaler 의 approval_status / rejection_reason 에
--       덮어쓰기로만 남았다. 재신청이 일어나면 이전 라운드의 거절 사유와 문제 서류가
--       사라져서, 화면이 요구하는 "신청 일시" 와 라운드별 이력을 낼 수 없다.
--
--  무엇 행 = 신청 1라운드. 최초 신청과 재신청마다 한 행이 생기고, 심사 결과는 그 행에
--       한 번 기록된다. 화면의 "신청 일시" = 최신 행 created_at,
--       거절 사유·문제 서류 = 최신 REJECTED 행.
--
--  wholesaler_document 는 스키마 변경 없이 append 규칙만 확정한다 —
--  재신청 시 행을 추가하고, type 별 최신 행이 현재 서류다.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.approval_request (
    id             bigserial   PRIMARY KEY,
    wholesaler_id  bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    status         varchar(20) NOT NULL DEFAULT 'PENDING',
    reason         varchar(500),                          -- 거절 사유. REJECTED 만
    document_types text[]      NOT NULL DEFAULT '{}',     -- 문제 서류. REJECTED 만
    actor          varchar(50),                           -- 심사자. 심사 전 NULL
    created_at     timestamptz NOT NULL DEFAULT now(),    -- 신청 일시
    decided_at     timestamptz,                           -- 심사 일시. 심사 전 NULL
    CONSTRAINT approval_request_status_ck
        CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    -- 값 어휘는 wholesaler_document_type_ck 와 같다. 배열 전체가 부분집합이어야 통과
    CONSTRAINT approval_request_doc_types_ck
        CHECK (document_types <@ ARRAY['BIZ_REG','CEO_ID','STORE_PHOTO']::text[])
);

-- 화면의 "신청 일시" = 최신 행. 라운드 이력 조회의 축
CREATE INDEX approval_request_latest_idx
    ON wholesale.approval_request (wholesaler_id, created_at DESC);

-- 심사 대기는 한 번에 하나. 재신청 연타가 라운드를 둘로 벌리지 못하게 한다
CREATE UNIQUE INDEX approval_request_pending_uk
    ON wholesale.approval_request (wholesaler_id) WHERE status = 'PENDING';

-- 거절 사유는 approval_request 최신 REJECTED 행에서 파생한다. 파생값 이중 저장 금지
ALTER TABLE wholesale.wholesaler DROP COLUMN rejection_reason;
