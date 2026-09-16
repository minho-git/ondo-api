-- 상품 목록 쿼리 인덱스 실험 (MUL-118 · 2026-09-14 · medium 데이터)
--
-- ⚠️ 로컬 도매 DB 전용. 마이그레이션이 아니다. 효과 확인용으로 만들었다 지운다.
--
--   만들기  docker exec -i ondo-wholesale-db psql -U ondo -d ondo_wholesale -v action=create < experiments/listing-index.sql
--   지우기  docker exec -i ondo-wholesale-db psql -U ondo -d ondo_wholesale -v action=drop   < experiments/listing-index.sql
--
-- 결과 (EXPLAIN ANALYZE, 게시글 1만 · 옵션 6만 · 이미지 2만)
--
--   쿼리                     전        후
--   20개 고르기              29.4ms    0.23ms   게시글 전부 읽고 정렬 → 최신순 인덱스를 20개까지만
--   20개 고르기 (5페이지)     30.3ms    0.66ms
--   20개 고르기 (카테고리)    30.4ms    1.06ms
--   전체 개수                42.4ms   31.0ms   인덱스로 안 준다 — 다 세야 한다
--   썸네일 20개              13.5ms    0.05ms   이미지 2만 개를 20번 훑던 걸 인덱스로
--
--   API 전체 (k6 · 초당 20건)  p95 53.8ms → 31.0ms
--
-- 전체 개수의 26ms 중 24ms 는 "게시글마다 살 수 있는 옵션이 있나" 확인이다.
-- 확인을 빼고 세면 2.2ms, 게시글 테이블만 세면 0.7ms.
--
--
-- large 에서 끝까지 (2026-09-15 · 게시글 10만 · 카테고리마다 1,664~1,868개)
--
--   k6 single listings, 30초              p95        실패
--   그대로                                5,059ms    100%   소매→도매 5초 타임아웃
--   인덱스만                              5,063ms     38%   개수 세기(115ms)가 계속 쌓인다
--   개수 1,001 까지만 (인덱스 없이)          201ms      0%   listing-count-cap.patch
--   인덱스 + 개수 1,001 까지만               28ms      0%
--     같은 조건 초당 100건                   21ms      0%
--     같은 조건 초당 200건                   30ms      0%
--
-- 여기 데이터는 카테고리마다 1,000개가 넘어서 모든 요청이 상한에 걸린다.
-- 맞는 게 적은 검색(0건)은 상한까지 못 채우니 그대로 다 본다 — 23~30ms.
-- 개수 상한은 화면이 "1,000개 이상" 을 받아도 되는지 창은 확인이 먼저다.
--
-- product(category_id) 인덱스도 만들어봤는데 플래너가 안 썼다 — 최신순 인덱스로 충분했다. 안 넣는다.

\if :{?action}
\else
  \echo 'action 을 준다 — -v action=create 또는 -v action=drop'
  \quit
\endif

SELECT :'action' = 'create' AS is_create \gset

\if :is_create
  -- 목록 정렬(ORDER BY season_started_at DESC NULLS LAST, id DESC)과 보이는 조건을 그대로 딴다
  CREATE INDEX IF NOT EXISTS exp_listing_visible_order ON wholesale.listing (season_started_at DESC NULLS LAST, id DESC)
    WHERE status = 'ON_SALE' AND deleted_at IS NULL;
  -- 카드 썸네일: WHERE listing_id = ? ORDER BY sort_order, id LIMIT 1
  CREATE INDEX IF NOT EXISTS exp_listing_image_listing ON wholesale.listing_image (listing_id, sort_order, id);
  ANALYZE wholesale.listing, wholesale.listing_image;
  \echo '실험 인덱스 만들었다'
\else
  DROP INDEX IF EXISTS wholesale.exp_listing_visible_order;
  DROP INDEX IF EXISTS wholesale.exp_listing_image_listing;
  DROP INDEX IF EXISTS wholesale.exp_product_category;
  ANALYZE wholesale.listing, wholesale.listing_image, wholesale.product;
  \echo '실험 인덱스 지웠다'
\endif
