-- ═══════════════════════════════════════════════════════════════
--  On도 통합 스키마 · PostgreSQL
--  2026-08-23 작성 · 2026-08-25 V1 확정 (ERDCloud 내보내기와 대조 완료 · partner.contact_* 제거)
--
--  기반   ondo_erd_v260823_1524.mermaid (도매)        - 팀원
--  추가   결제·정산·거래처 PARTNER/PAYMENT/RECEIVABLE_LEDGER - SQL 판
--  추가   retail 10개 (세션 2개 포함)                                  - 민호
--
--  PG 로 옮기며 바꾼 것
--    1  ORDER -> orders          ORDER 는 PostgreSQL 예약어라 그대로 못 쓴다
--    2  datetime -> timestamptz  PG 에 datetime 타입이 없다
--    3  decimal  -> numeric
--    4  id       -> bigserial    팀원 ERD 표기(BIGSERIAL)를 따랐다
--                                (현대식은 GENERATED ALWAYS AS IDENTITY. 바꾸려면 일괄 치환)
--    5  created_at/updated_at    팀원 주석 "전 테이블 공통, 다이어그램에선 생략"을 실제로 넣었다
--
--  경계   retail <-> wholesale 참조에는 FK 를 걸지 않는다 (논리 참조)
-- ═══════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS common;
CREATE SCHEMA IF NOT EXISTS wholesale;
CREATE SCHEMA IF NOT EXISTS retail;


-- ═══════════════════════════════════════════════════════════════
--  common
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE common.category (
    id          bigserial   PRIMARY KEY,
    parent_id   bigint      REFERENCES common.category (id),   -- depth 1 은 NULL
    name        varchar(50) NOT NULL,
    depth       smallint    NOT NULL,                          -- 1~3
    sort_order  int         NOT NULL DEFAULT 0,
    is_active   boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT category_depth_ck CHECK (depth BETWEEN 1 AND 3)
);

CREATE TABLE common.color_group (
    id          bigserial   PRIMARY KEY,
    name        varchar(30) NOT NULL,                          -- 무채색 · 데님워싱 …
    sort_order  int         NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE common.color (
    id          bigserial   PRIMARY KEY,
    group_id    bigint      NOT NULL REFERENCES common.color_group (id),
    name        varchar(30) NOT NULL,                          -- 진청
    hex         char(7),                                       -- #RRGGBB
    sort_order  int         NOT NULL DEFAULT 0,                -- 그룹 내 순서
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 계정
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.wholesaler (
    id                   bigserial    PRIMARY KEY,
    email                varchar(100) NOT NULL,                -- 로그인 계정
    password_hash        varchar(255) NOT NULL,                -- BCrypt. 평문 금지
    phone                varchar(20),                          -- 개인 휴대전화
    biz_reg_no           varchar(20)  NOT NULL,                -- 사업자등록번호 10자리
    biz_name             varchar(50)  NOT NULL,                -- 상호. 소매 화면 노출명 겸용
    biz_owner_name       varchar(50)  NOT NULL,                -- 대표자명
    store_phone          varchar(20),
    store_building       varchar(50),                          -- 누죤 · APM 등 상가 건물명
    store_unit           varchar(50),                          -- 3층 C-25
    biz_category         varchar(50),                          -- 등록증 종목 표기 그대로
    bank_name            varchar(30),                          -- 입금 계좌. 미등록이면 NULL (대시보드 배너)
    bank_account_no      varchar(30),
    bank_account_holder  varchar(50),
    approval_status      varchar(20)  NOT NULL DEFAULT 'PENDING',
    rejection_reason     varchar(500),
    approved_at          timestamptz,
    last_product_seq     int          NOT NULL DEFAULT 0,      -- 품번 채번
    last_order_seq       int          NOT NULL DEFAULT 0,      -- 주문번호 채번 (D-064)
    last_outbound_seq    int          NOT NULL DEFAULT 0,      -- 출고번호 채번 (D-075)
    last_statement_date  date,                                 -- 장끼 날짜별 채번 (D-076)
    last_statement_seq   int          NOT NULL DEFAULT 0,
    deleted_at           timestamptz,                          -- soft delete
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT wholesaler_email_uk      UNIQUE (email),
    CONSTRAINT wholesaler_biz_reg_no_uk UNIQUE (biz_reg_no),
    CONSTRAINT wholesaler_status_ck
        CHECK (approval_status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE TABLE wholesale.wholesaler_consent (
    id             bigserial   PRIMARY KEY,                    -- APPEND-ONLY 원장
    wholesaler_id  bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    type           varchar(30) NOT NULL,
    agreed         boolean     NOT NULL,                       -- 철회 = false 행 추가. 최신 행이 현재 상태
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT wholesaler_consent_type_ck CHECK (type IN
        ('TERMS','PRIVACY','MARKETING_SMS','MARKETING_EMAIL','MARKETING_PUSH','INFO_CONFIRM'))
);

CREATE TABLE wholesale.wholesaler_document (
    id             bigserial    PRIMARY KEY,
    wholesaler_id  bigint       NOT NULL REFERENCES wholesale.wholesaler (id),
    type           varchar(30)  NOT NULL,
    file_url       varchar(500) NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT wholesaler_document_type_ck CHECK (type IN ('BIZ_REG','CEO_ID','STORE_PHOTO'))
);


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 세션
--  Spring Session JDBC 가 요구하는 모양 그대로다. retail.spring_session 과 같다.
--  소매와 나눈다 — 사용자 유형이 다르고 스키마 경계와 맞다.
--  인덱스 이름에 wh_ 를 붙인 건 인덱스가 스키마를 넘어 유일해야 하기 때문이다.
--  설정: spring.session.jdbc.table-name=wholesale.spring_session
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.spring_session (
    primary_id             char(36)     NOT NULL,               -- 내부 키
    session_id             char(36)     NOT NULL,               -- 쿠키에 실리는 이름표. 재발급되면 바뀐다
    creation_time          bigint       NOT NULL,               -- epoch millis
    last_access_time       bigint       NOT NULL,
    max_inactive_interval  int          NOT NULL,               -- 초. 이만큼 안 쓰면 만료
    expiry_time            bigint       NOT NULL,
    principal_name         varchar(100),                        -- 로그인 계정. 계정 정지 시 이걸로 찾아 지운다
    CONSTRAINT wh_spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX wh_spring_session_id_uk         ON wholesale.spring_session (session_id);
CREATE INDEX        wh_spring_session_expiry_idx    ON wholesale.spring_session (expiry_time);
CREATE INDEX        wh_spring_session_principal_idx ON wholesale.spring_session (principal_name);

CREATE TABLE wholesale.spring_session_attributes (
    session_primary_id  char(36)     NOT NULL,
    attribute_name      varchar(200) NOT NULL,
    attribute_bytes     bytea        NOT NULL,                  -- 자바 직렬화. SQL 로는 안 읽힌다
    CONSTRAINT wh_spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT wh_spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES wholesale.spring_session (primary_id) ON DELETE CASCADE
);


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 상품
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.product (
    id                 bigserial   PRIMARY KEY,
    wholesaler_id      bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    product_number     int         NOT NULL,                   -- 도매처별 연번. 영구 결번 (D-004)
                                                               -- 표시 코드(SU-18)는 프론트 조립
    name               varchar(100) NOT NULL,
    category_id        bigint      NOT NULL REFERENCES common.category (id),  -- 리프(depth 3)만 (D-058)
    last_variant_seq   int         NOT NULL DEFAULT 0,         -- variant 채번
    deleted_at         timestamptz,                            -- 삭제 시 listing 도 함께
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT product_number_uk UNIQUE (wholesaler_id, product_number)   -- full. 영구 결번 의도
);

CREATE TABLE wholesale.color_option (
    id          bigserial    PRIMARY KEY,
    product_id  bigint       NOT NULL REFERENCES wholesale.product (id),
    color_id    bigint       NOT NULL REFERENCES common.color (id),
    image_url   varchar(500),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT color_option_uk UNIQUE (product_id, color_id)   -- full. 지우지 않는다 (D-053)
);

CREATE TABLE wholesale.variant (
    id                       bigserial     PRIMARY KEY,
    color_option_id          bigint        NOT NULL REFERENCES wholesale.color_option (id),
    product_id               bigint        NOT NULL REFERENCES wholesale.product (id),  -- 중복 보유 (D-055). 불변
    size                     varchar(10)   NOT NULL,           -- CHECK 은 아래. 낱장은 F 가 아니라 FREE
    variant_seq              int           NOT NULL,           -- 표시코드는 파생, 저장 안 함
    stock_qty                int           NOT NULL DEFAULT 0, -- 현재고
    reserved_qty             int           NOT NULL DEFAULT 0, -- 주문처리중. 도메인 메서드로만
    avg_cost                 numeric(16,6) NOT NULL DEFAULT 0, -- FIFO 잔량 실원가 캐시. lot 이 진실
    expected_inbound_date    date,                             -- 미송 예상 입고일 (D-067)
    expected_inbound_reason  varchar(200),
    deleted_at               timestamptz,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT variant_seq_uk  UNIQUE (product_id, variant_seq),        -- full. 영구 결번
    CONSTRAINT variant_size_ck     CHECK (size IN ('XS','S','M','L','XL','2XL','FREE')),
    CONSTRAINT variant_stock_ck    CHECK (stock_qty >= 0),
    CONSTRAINT variant_reserved_ck CHECK (reserved_qty >= 0)
);

-- 사이즈 재추가 허용 (D-053). 살아있는 행에서만 유일
CREATE UNIQUE INDEX variant_size_uk
    ON wholesale.variant (color_option_id, size) WHERE deleted_at IS NULL;

CREATE TABLE wholesale.listing (
    id                   bigserial   PRIMARY KEY,
    product_id           bigint      NOT NULL REFERENCES wholesale.product (id),
    title                varchar(100) NOT NULL,                -- 검색용 이름. 품명과 별개
    description          text,
    single_piece_allowed boolean     NOT NULL DEFAULT false,   -- 낱장 등록
    status               varchar(20) NOT NULL DEFAULT 'ON_SALE',
    season_started_at    timestamptz,                          -- ON_SALE 진입 시 갱신
    season_ended_at      timestamptz,                          -- SEASON_ENDED 진입 시 갱신
    deleted_at           timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT listing_product_uk UNIQUE (product_id),         -- full. 상품당 게시글 1건 (D-079)
    CONSTRAINT listing_status_ck  CHECK (status IN ('ON_SALE','SEASON_ENDED'))
);

CREATE TABLE wholesale.listing_image (
    id          bigserial    PRIMARY KEY,
    listing_id  bigint       NOT NULL REFERENCES wholesale.listing (id),
    url         varchar(500) NOT NULL,
    sort_order  int          NOT NULL DEFAULT 0,               -- 0 = 대표. 전체 PUT 로 재배열
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- 행이 있으면 = 마켓에 올린 옵션. 지우지 않는다 (D-053)
-- 나중에 "옵션 내리기" 가 필요해지면 is_listed 가 있어야 한다 -> 팀원 확인
CREATE TABLE wholesale.listing_variant (
    listing_id   bigint      NOT NULL REFERENCES wholesale.listing (id),
    variant_id   bigint      NOT NULL REFERENCES wholesale.variant (id),
    sale_price   int         NOT NULL,                         -- 판매가의 유일한 저장 위치
    order_limit  int         NOT NULL DEFAULT 0,               -- 1회 주문당 최대. 0 = 무제한 (D-057)
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (listing_id, variant_id)
);


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 재고
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.inbound (
    id             bigserial   PRIMARY KEY,
    wholesaler_id  bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    received_at    timestamptz NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE wholesale.inbound_item (
    id             bigserial     PRIMARY KEY,
    inbound_id     bigint        NOT NULL REFERENCES wholesale.inbound (id),
    variant_id     bigint        NOT NULL REFERENCES wholesale.variant (id),
    qty            int           NOT NULL,                     -- 입고량. 불변
    remaining_qty  int           NOT NULL,                     -- lot 잔량. 오래된 lot 부터 차감
    unit_cost      numeric(14,4) NOT NULL,                     -- 로트별 매입단가
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT inbound_item_remaining_ck CHECK (remaining_qty >= 0)
);

CREATE TABLE wholesale.stock_movement (
    id          bigserial   PRIMARY KEY,                       -- APPEND-ONLY 원장
    variant_id  bigint      NOT NULL REFERENCES wholesale.variant (id),
    type        varchar(20) NOT NULL,
    qty_change  int         NOT NULL,                          -- 부호 포함
    qty_after   int         NOT NULL,                          -- 파생이지만 감사 목적으로 저장
    ref_type    varchar(30) NOT NULL,                          -- INBOUND_ITEM · ORDER …
    ref_id      bigint      NOT NULL,                          -- 다형 참조라 FK 불가
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT stock_movement_type_ck CHECK (type IN ('IN','OUT','ADJUST'))
);


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 거래처   [M-1] SQL 판에서 가져옴
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.partner (
    id                  bigserial   PRIMARY KEY,
    wholesaler_id       bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    retailer_id         bigint      NOT NULL,                  -- 논리 참조(경계). FK 없음
    retailer_name       varchar(50) NOT NULL,                  -- 소매 상호 스냅샷. U-15 의 답
    trade_type          varchar(20) NOT NULL DEFAULT 'NORMAL', -- NORMAL | CREDIT(외상)
    credit_days         int,                                   -- 외상 기간. NULL = 즉시
    receivable_balance  bigint      NOT NULL DEFAULT 0,        -- 미수 요약. 진실은 receivable_ledger
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT partner_uk         UNIQUE (wholesaler_id, retailer_id),
    CONSTRAINT partner_trade_ck   CHECK (trade_type IN ('NORMAL','CREDIT'))
);


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 주문
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.orders (                                -- ORDER 는 예약어
    id               bigserial   PRIMARY KEY,
    order_number     int         NOT NULL,                     -- 도매처별 연번 (D-064)
                                                               -- 표시 코드(ORD-001)는 프론트 조립
    retail_order_id  bigint,                                   -- 소매 통합 주문서. 논리 참조. 도매 직접 주문은 NULL
    partner_id       bigint      NOT NULL REFERENCES wholesale.partner (id),      -- [M-2]
    wholesaler_id    bigint      NOT NULL REFERENCES wholesale.wholesaler (id),   -- 비정규화
    status           varchar(20) NOT NULL DEFAULT 'NEW',       -- 출고 진행도는 파생
    payment_term     varchar(20) NOT NULL,                     -- 결제 조건(약속)
    receive_method   varchar(20) NOT NULL,                     -- 수령 방법 (U-11)
    agent_name       varchar(50),                              -- 사입삼촌. 장끼에 수령인으로 찍힌다
    agent_phone      varchar(20),                              -- 소매 주문 접수 API 로 넘어온다
    ordered_at       timestamptz NOT NULL,
    confirmed_at     timestamptz,                              -- CONFIRMED 전이 시각
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT orders_number_uk    UNIQUE (wholesaler_id, order_number),
    -- 주문 생성 멱등성 (D-052). retail_order_id 가 NULL 인 도매 직접 주문은 이 제약을 통과한다
    CONSTRAINT orders_idempotent_uk UNIQUE (retail_order_id, wholesaler_id),
    CONSTRAINT orders_status_ck    CHECK (status IN ('NEW','CONFIRMED','CANCELLED'))
);

CREATE TABLE wholesale.order_item (
    id             bigserial   PRIMARY KEY,
    order_id       bigint      NOT NULL REFERENCES wholesale.orders (id),
    variant_id     bigint      NOT NULL REFERENCES wholesale.variant (id),
    qty            int         NOT NULL,                       -- 주문수량
    unit_price     int         NOT NULL,                       -- 주문 시점 판매가 스냅샷
    allocated_qty  int         NOT NULL DEFAULT 0,             -- 할당 카운터. FOR UPDATE 보호
    shipped_qty    int         NOT NULL DEFAULT 0,             -- 출고 카운터
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT order_item_qty_ck    CHECK (qty > 0),
    CONSTRAINT order_item_ladder_ck CHECK (shipped_qty <= allocated_qty AND allocated_qty <= qty)
);

CREATE TABLE wholesale.backorder (
    id             bigserial   PRIMARY KEY,
    order_item_id  bigint      NOT NULL REFERENCES wholesale.order_item (id),
    qty            int         NOT NULL,                       -- 원래 미송량. 불변
    status         varchar(20) NOT NULL DEFAULT 'OPEN',        -- RESOLVED 는 조회 성능 위해 저장
    created_at     timestamptz NOT NULL DEFAULT now(),         -- FIFO 기준
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT backorder_status_ck CHECK (status IN ('OPEN','RESOLVED','CANCELLED'))
);

CREATE INDEX backorder_open_idx
    ON wholesale.backorder (order_item_id, created_at) WHERE status = 'OPEN';


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 포장 · 출고
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.allocation_batch (
    id             bigserial   PRIMARY KEY,                    -- 배분 1회 = 1행 (D-070)
    wholesaler_id  bigint      NOT NULL REFERENCES wholesale.wholesaler (id),
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE wholesale.outbound (
    id                bigserial   PRIMARY KEY,
    wholesaler_id     bigint      NOT NULL REFERENCES wholesale.wholesaler (id),  -- 비정규화 (D-071)
    partner_id        bigint      NOT NULL REFERENCES wholesale.partner (id),     -- [M-3] 묶음 조건
    outbound_number   varchar(20) NOT NULL,                    -- PKG-001 (D-075)
    statement_number  varchar(30),                             -- JG-20260818-001 장끼 (D-076)
    shipped_at        timestamptz,                             -- NULL=포장완료 · 값=출고완료 (D-074)
    created_at        timestamptz NOT NULL DEFAULT now(),      -- 문서 생성 = 포장완료
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT outbound_number_uk UNIQUE (wholesaler_id, outbound_number)
);

CREATE TABLE wholesale.packing (
    id           bigserial   PRIMARY KEY,                      -- 봉투 1개 = 1행
    order_id     bigint      NOT NULL REFERENCES wholesale.orders (id),
    outbound_id  bigint      REFERENCES wholesale.outbound (id),  -- NULL = 아직 매장에 있음
    status       varchar(20) NOT NULL DEFAULT 'READY',
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT packing_status_ck CHECK (status IN ('READY','PACKED'))
);

-- "포장 대기열" 은 실체가 없다 = 이 조건 (D-073)
CREATE INDEX packing_queue_idx
    ON wholesale.packing (order_id) WHERE status = 'READY' AND outbound_id IS NULL;

CREATE TABLE wholesale.packing_item (
    id                   bigserial   PRIMARY KEY,
    packing_id           bigint      NOT NULL REFERENCES wholesale.packing (id),
    order_item_id        bigint      NOT NULL REFERENCES wholesale.order_item (id),
    backorder_id         bigint      REFERENCES wholesale.backorder (id),   -- 값 = 미송 배분분(해소 기록)
    allocation_batch_id  bigint      NOT NULL REFERENCES wholesale.allocation_batch (id),
    qty                  int         NOT NULL,
    deleted_at           timestamptz,                          -- 삭제 = 배분취소 = 미송부활
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT packing_item_qty_ck CHECK (qty > 0)
);


-- ═══════════════════════════════════════════════════════════════
--  wholesale · 정산   [M-4][M-5] SQL 판에서 가져옴
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE wholesale.payment (
    id             bigserial   PRIMARY KEY,
    partner_id     bigint      NOT NULL REFERENCES wholesale.partner (id),
    wholesaler_id  bigint      NOT NULL REFERENCES wholesale.wholesaler (id),  -- 비정규화
    request_id     varchar(64) NOT NULL,                       -- 연타·재시도가 입금을 두 번 만들지 못하게
    amount         bigint      NOT NULL,
    paid_by        varchar(20) NOT NULL,                       -- RETAILER | AGENT. payment_term 과 독립
    payer_name     varchar(50),                                -- 통장에 찍힌 표기 그대로
    method         varchar(20) NOT NULL,                       -- CASH | BANK_TRANSFER
    paid_at        timestamptz NOT NULL,
    memo           varchar(255),
    voided_at      timestamptz,                                -- 무효 표시. 행을 지우거나 고치지 않는다
    void_reason    varchar(200),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payment_request_uk UNIQUE (request_id),
    CONSTRAINT payment_paid_by_ck CHECK (paid_by IN ('RETAILER','AGENT')),
    CONSTRAINT payment_method_ck  CHECK (method  IN ('CASH','BANK_TRANSFER')),
    CONSTRAINT payment_amount_ck  CHECK (amount > 0)
);

CREATE TABLE wholesale.receivable_ledger (
    id             bigserial   PRIMARY KEY,                    -- APPEND-ONLY 원장
    partner_id     bigint      NOT NULL REFERENCES wholesale.partner (id),  -- 원장 조회의 축
    request_id     varchar(64) NOT NULL,                       -- 연타·재시도만 막는다
    entry_type     varchar(20) NOT NULL,                       -- OUTBOUND(+) | PAYMENT(-) | ADJUST(±)
    delta          bigint      NOT NULL,                       -- 부호 포함
    balance_after  bigint      NOT NULL,                       -- 검산용. partner 행 락으로 직렬화
    order_id       bigint      NOT NULL REFERENCES wholesale.orders (id),   -- 모든 행이 주문을 가리킨다
    outbound_id    bigint      REFERENCES wholesale.outbound (id),          -- OUTBOUND 행만
    payment_id     bigint      REFERENCES wholesale.payment (id),           -- PAYMENT 행 필수
    memo           varchar(200),                               -- ADJUST 필수
    actor          varchar(50),
    occurred_at    timestamptz NOT NULL,                       -- [X-1] 미수 발생 = 출고 시점
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT receivable_ledger_request_uk UNIQUE (request_id),
    CONSTRAINT receivable_ledger_type_ck
        CHECK (entry_type IN ('OUTBOUND','PAYMENT','ADJUST'))
);

CREATE INDEX receivable_ledger_partner_idx
    ON wholesale.receivable_ledger (partner_id, occurred_at DESC);


-- ═══════════════════════════════════════════════════════════════
--  retail · 민호
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE retail.retailer (
    id               bigserial    PRIMARY KEY,
    email            varchar(100) NOT NULL,
    password         varchar(255) NOT NULL,                    -- BCrypt
    shop_name        varchar(50)  NOT NULL,                    -- 상호. 도매 거래처 목록에 노출
    approval_status  varchar(20)  NOT NULL DEFAULT 'PENDING',  -- 시드는 APPROVED (결정.md 08-23)
    approved_at      timestamptz,                              -- 최초 승인 시각. 재심사 PENDING 과 구분하는 근거
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT retailer_status_ck
        CHECK (approval_status IN ('PENDING','APPROVED','REJECTED'))
);

-- 대소문자를 무시한다. Bom@x.com 과 bom@x.com 은 같은 계정
CREATE UNIQUE INDEX retailer_email_uk ON retail.retailer (lower(email));

CREATE TABLE retail.retailer_private (
    retailer_id  bigint       PRIMARY KEY REFERENCES retail.retailer (id),
    owner_name   varchar(255) NOT NULL,                        -- AES
    mobile       varchar(255) NOT NULL,                        -- AES
    biz_reg_no   varchar(255) NOT NULL,                        -- AES
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE retail.retailer_doc (
    id           bigserial    PRIMARY KEY,
    retailer_id  bigint       NOT NULL REFERENCES retail.retailer (id),
    doc_type     varchar(30)  NOT NULL DEFAULT 'BIZ_LICENSE',  -- 소매는 1종
    file_url     varchar(500) NOT NULL,                        -- [R-2] 암호화 대상으로 볼지 미정
    is_current   boolean      NOT NULL DEFAULT true,           -- 재제출하면 이전 건 false
    created_at   timestamptz  NOT NULL DEFAULT now()
);

-- 현재 문서는 종류마다 딱 하나
CREATE UNIQUE INDEX retailer_doc_current_uk
    ON retail.retailer_doc (retailer_id, doc_type) WHERE is_current;

CREATE TABLE retail.terms_agreement (
    id           bigserial   PRIMARY KEY,
    retailer_id  bigint      NOT NULL REFERENCES retail.retailer (id),
    terms_type   varchar(30) NOT NULL,                         -- 소매는 2종. channel 없음
    agreed_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT terms_agreement_type_ck CHECK (terms_type IN ('SERVICE','PRIVACY'))
);

CREATE TABLE retail.approval_history (
    id           bigserial    PRIMARY KEY,
    retailer_id  bigint       NOT NULL REFERENCES retail.retailer (id),
    from_status  varchar(20),                                  -- 최초 신청은 NULL
    to_status    varchar(20)  NOT NULL,
    reason       varchar(500),                                 -- 거절 사유. 이력에만 남긴다
    actor        varchar(50)  NOT NULL,                        -- SYSTEM | 운영자 이메일
    created_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX approval_history_recent_idx
    ON retail.approval_history (retailer_id, created_at DESC);

CREATE TABLE retail.cart_item (
    id           bigserial   PRIMARY KEY,
    retailer_id  bigint      NOT NULL REFERENCES retail.retailer (id),
    variant_id   bigint      NOT NULL,                         -- [M-6] 논리 참조(경계). FK 없음
    qty          int         NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cart_item_qty_ck CHECK (qty > 0),
    CONSTRAINT cart_item_uk     UNIQUE (retailer_id, variant_id)
);

CREATE TABLE retail.favorite (
    id           bigserial   PRIMARY KEY,
    retailer_id  bigint      NOT NULL REFERENCES retail.retailer (id),
    listing_id   bigint      NOT NULL,                         -- 논리 참조(경계). 찜 대상은 게시물
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT favorite_uk UNIQUE (retailer_id, listing_id)
);

CREATE INDEX favorite_recent_idx
    ON retail.favorite (retailer_id, created_at DESC);

CREATE TABLE retail.order_group (
    id            bigserial   PRIMARY KEY,
    retailer_id   bigint      NOT NULL REFERENCES retail.retailer (id),
    request_id    varchar(64) NOT NULL,                        -- [M-7] 브라우저가 만든 번호. 연타를 막는다
    order_no      varchar(30) NOT NULL,                        -- 20260717-1152-0088
    agent_name    varchar(50),                                 -- 사입삼촌. 도매처가 여럿이어도 사람은 하나
    agent_phone   varchar(20),
    total_amount  bigint      NOT NULL,                        -- 접수 성공분 합계. 주문 시점 고정
                                                              -- 취소돼도 안 바꾼다. 경계 너머 집계라 저장이 정당
    ordered_at    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT order_group_request_uk UNIQUE (request_id),
    CONSTRAINT order_group_no_uk      UNIQUE (order_no)
);

CREATE INDEX order_group_recent_idx
    ON retail.order_group (retailer_id, ordered_at DESC);


-- ═══════════════════════════════════════════════════════════════
--  retail · 세션
--  Spring Session JDBC 가 요구하는 모양 그대로다. 컬럼 이름·타입을 우리가 못 정한다.
--  시간이 timestamptz 가 아니라 bigint(밀리초)인 것도 그쪽 규격이다.
--  설정: spring.session.jdbc.table-name=retail.spring_session
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE retail.spring_session (
    primary_id             char(36)     NOT NULL,               -- 내부 키
    session_id             char(36)     NOT NULL,               -- 쿠키에 실리는 이름표. 재발급되면 바뀐다
    creation_time          bigint       NOT NULL,               -- epoch millis
    last_access_time       bigint       NOT NULL,
    max_inactive_interval  int          NOT NULL,               -- 초. 이만큼 안 쓰면 만료
    expiry_time            bigint       NOT NULL,
    principal_name         varchar(100),                        -- 로그인 계정. 계정 정지 시 이걸로 찾아 지운다
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_id_uk         ON retail.spring_session (session_id);
CREATE INDEX        spring_session_expiry_idx    ON retail.spring_session (expiry_time);
CREATE INDEX        spring_session_principal_idx ON retail.spring_session (principal_name);

CREATE TABLE retail.spring_session_attributes (
    session_primary_id  char(36)     NOT NULL,
    attribute_name      varchar(200) NOT NULL,
    attribute_bytes     bytea        NOT NULL,                  -- 자바 직렬화. SQL 로는 안 읽힌다
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES retail.spring_session (primary_id) ON DELETE CASCADE
);


-- ═══════════════════════════════════════════════════════════════
--  권한 — 경계를 DB 가 강제한다
--  서로 남의 스키마를 안 본다. 읽기도 쓰기도 API (2026-08-23 확정)
-- ═══════════════════════════════════════════════════════════════

-- CREATE ROLE retail_api    LOGIN PASSWORD '...';
-- CREATE ROLE wholesale_api LOGIN PASSWORD '...';

-- 소매
-- GRANT USAGE ON SCHEMA common, retail TO retail_api;
-- GRANT SELECT ON ALL TABLES IN SCHEMA common TO retail_api;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA retail TO retail_api;
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA retail TO retail_api;   -- bigserial 채번. 없으면 INSERT 가 전부 막힌다

-- 도매
-- GRANT USAGE ON SCHEMA common, wholesale TO wholesale_api;
-- GRANT SELECT ON ALL TABLES IN SCHEMA common TO wholesale_api;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA wholesale TO wholesale_api;
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA wholesale TO wholesale_api;

-- 앞으로 만들 테이블에도 자동으로 붙는다. 위 GRANT 는 지금 있는 것만 잡는다
-- (테이블을 만드는 그 역할로 실행해야 한다)
-- ALTER DEFAULT PRIVILEGES IN SCHEMA common
--     GRANT SELECT ON TABLES TO retail_api, wholesale_api;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA retail
--     GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO retail_api;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA retail    GRANT USAGE ON SEQUENCES TO retail_api;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA wholesale
--     GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wholesale_api;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA wholesale GRANT USAGE ON SEQUENCES TO wholesale_api;
