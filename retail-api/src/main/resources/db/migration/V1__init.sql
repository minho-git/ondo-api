-- ═══════════════════════════════════════════════════════════════
--  On도 소매 스키마 · PostgreSQL
--  2026-08-23 작성 · 2026-08-25 확정 · 2026-08-29 DB 분리로 도매/소매 파일을 나눔
--
-- ═══════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS retail;


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
--  권한
--  역할 생성은 DBA 몫이라 주석으로 둔다
-- ═══════════════════════════════════════════════════════════════

-- CREATE ROLE retail_api LOGIN PASSWORD '...';

-- GRANT USAGE ON SCHEMA retail TO retail_api;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA retail TO retail_api;
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA retail TO retail_api;   -- bigserial 채번. 없으면 INSERT 가 전부 막힌다

-- 앞으로 만들 테이블에도 자동으로 붙는다. 위 GRANT 는 지금 있는 것만 잡는다
-- (테이블을 만드는 그 역할로 실행해야 한다)
-- ALTER DEFAULT PRIVILEGES IN SCHEMA retail
--     GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO retail_api;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA retail GRANT USAGE ON SEQUENCES TO retail_api;
