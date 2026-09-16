"""
소매 부하 테스트용 대량 데이터 (MUL-118).

두 DB 에 넣을 SQL 을 한 번에 만든다 — out/wholesale.sql · out/retail.sql

    python3 generate.py small     # 도매처 50 · 상품 1천 · 소매처 100 · 주문서 1천
    python3 generate.py medium    # 도매처 200 · 상품 1만 · 소매처 500 · 주문서 1만
    python3 generate.py large     # 도매처 500 · 상품 10만 · 소매처 1천 · 주문서 10만

넣는 건 load.sh 가 한다.

■ 왜 파이썬이 두 파일을 같이 만드나
  도매·소매 DB 가 갈라져 있어 FK 가 없다. 소매 order_group.id 와 도매 orders.retail_order_id,
  소매 retailer.id 와 도매 partner.retailer_id 가 맞아야 주문 내역이 조회된다.
  SQL 로 따로 만들면 이 짝을 두 번 계산해야 한다. 한 곳에서 만들고 양쪽에 쓴다.

■ id 는 전부 1,000,000 부터
  기존 시드(101 · 1001 · 6001 …)와 로컬에서 직접 만든 데이터와 안 섞인다.
  다시 넣을 때 1,000,000 이상을 지우고 새로 넣는다 — 규모를 바꿔 여러 번 돌릴 수 있다.

  ⚠️ 넣은 뒤 시퀀스를 끝 번호로 민다. 그 뒤 로컬에서 새로 만드는 행도 이 범위라,
     다시 넣으면 같이 지워진다. 부하 테스트 중 생긴 주문을 치우는 게 목적이라 의도한 동작이다.

■ 난수 시드를 고정한다
  같은 규모면 매번 똑같은 데이터가 나온다. 측정 결과끼리 비교하려면 데이터가 같아야 한다.
"""
import csv
import io
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

SCALES = {
    #          도매처  상품      소매처  주문서
    "small":  (50,     1_000,    100,    1_000),
    "medium": (200,    10_000,   500,    10_000),
    "large":  (500,    100_000,  1_000,  100_000),
}

BASE = 1_000_000
SEED = 118

COLORS_PER_PRODUCT = 2           # 동대문 상품은 보통 2~3컬러
SIZES = ["S", "M", "L"]          # variant_size_uk (color_option_id, size) 때문에 컬러 안에서 겹치면 안 된다
VARIANTS_PER_PRODUCT = COLORS_PER_PRODUCT * len(SIZES)
IMAGES_PER_LISTING = 2
PARTNERS_PER_RETAILER = 5        # 소매처 한 곳이 거래하는 도매처 수
CART_ITEMS_PER_RETAILER = 3      # 장바구니 보기가 빈 화면이 아니게

# 소매 시드(V2)와 같은 값 — ondo1234!
PASSWORD_HASH = "$2a$10$/1x6KQmy8JKooArqUSgxN.wkxxZvagI.JnLRPtg3Iwt2.g.jDFz4m"

KST = timezone(timedelta(hours=9))

BUILDINGS = ["청평화패션몰", "디오트", "APM", "누존", "테크노", "유어스", "스튜디오W"]
STYLES = ["루즈핏", "오버핏", "슬림", "크롭", "롱", "와이드", "베이직", "빈티지", "린넨", "코튼"]
ITEMS = ["셔츠", "블라우스", "니트", "가디건", "티셔츠", "팬츠", "스커트", "원피스", "자켓", "코트"]


def main():
    scale = sys.argv[1] if len(sys.argv) > 1 else "small"
    if scale not in SCALES:
        sys.exit(f"규모는 {', '.join(SCALES)} 중 하나다")
    w_count, p_count, r_count, o_count = SCALES[scale]
    if w_count < PARTNERS_PER_RETAILER:
        sys.exit("도매처가 소매처당 거래처 수보다 적으면 partner 가 겹친다")

    rng = random.Random(SEED)
    now = datetime.now(timezone.utc).replace(microsecond=0)

    leaf_categories = [111, 112, 113, 114, 115, 116, 121, 122, 123, 124, 131, 132, 141, 142, 143, 144,
                       151, 152, 153, 154, 155, 156, 157, 161, 162, 163, 164, 165, 171, 172, 173,
                       181, 182, 183, 211, 212, 213, 214, 215, 216, 221, 222, 223, 224, 225,
                       231, 232, 233, 234, 235, 241, 242, 243, 244, 251, 252]
    color_ids = list(range(1, 27))

    w = Tables()
    r = Tables()

    # ── 도매처 ────────────────────────────────────────────────
    products_of = {wid: [] for wid in range(1, w_count + 1)}
    for pid in range(1, p_count + 1):
        products_of[(pid - 1) % w_count + 1].append(pid)

    for wid in range(1, w_count + 1):
        w.add("wholesaler",
              ["id", "email", "password_hash", "phone", "biz_reg_no", "biz_name", "biz_owner_name",
               "store_phone", "store_building", "store_unit", "biz_category",
               "bank_name", "bank_account_no", "bank_account_holder",
               "approval_status", "approved_at", "last_product_seq", "last_order_seq"],
              [BASE + wid, f"load-w{wid:06d}@ondo.test", PASSWORD_HASH, f"0107{wid:07d}",
               f"9{wid:09d}", f"로드도매 {wid:04d}", "김로드", f"0227{wid:06d}",
               rng.choice(BUILDINGS), f"{rng.randint(1, 5)}층 {rng.randint(1, 60)}호", "여성의류",
               "국민은행", f"123456-{wid:06d}", "김로드",
               "APPROVED", ts(now - timedelta(days=100)), len(products_of[wid]), 0])

    # ── 상품 · 컬러 · 옵션 · 게시글 ─────────────────────────────
    sale_price_of = {}      # variant id → 판매가. 주문 항목 단가 스냅샷에 쓴다
    variants_of_product = {}

    for wid, pids in products_of.items():
        for number, pid in enumerate(pids, start=1):
            w.add("product", ["id", "wholesaler_id", "product_number", "name", "category_id", "last_variant_seq"],
                  [BASE + pid, BASE + wid, number, product_name(rng), rng.choice(leaf_categories),
                   VARIANTS_PER_PRODUCT])

            created = now - timedelta(days=rng.randint(0, 90))
            w.add("listing",
                  ["id", "product_id", "title", "description", "single_piece_allowed", "status",
                   "season_started_at"],
                  [BASE + pid, BASE + pid, product_name(rng), "부하 테스트용 상품.",
                   rng.random() < 0.3, "ON_SALE", ts(created)])

            for i in range(IMAGES_PER_LISTING):
                w.add("listing_image", ["id", "listing_id", "url", "sort_order"],
                      [BASE + (pid - 1) * IMAGES_PER_LISTING + i + 1, BASE + pid,
                       f"https://cdn.ondo.test/load/{pid}/{i + 1}.jpg", i])

            base_price = rng.randrange(8_000, 60_000, 500)
            seq = 0
            variants = []
            for c, color_id in enumerate(rng.sample(color_ids, COLORS_PER_PRODUCT)):
                coid = BASE + (pid - 1) * COLORS_PER_PRODUCT + c + 1
                w.add("color_option", ["id", "product_id", "color_id"], [coid, BASE + pid, color_id])
                for size in SIZES:
                    seq += 1
                    vid = BASE + (pid - 1) * VARIANTS_PER_PRODUCT + seq
                    # 10% 는 품절. 장바구니·주문서의 재고 부족 분기도 부하에 섞이게
                    stock = 0 if rng.random() < 0.1 else rng.randint(1, 50)
                    w.add("variant", ["id", "color_option_id", "product_id", "size", "variant_seq", "stock_qty"],
                          [vid, coid, BASE + pid, size, seq, stock])
                    price = base_price + (1_000 if size == "L" else 0)
                    w.add("listing_variant", ["listing_id", "variant_id", "sale_price", "order_limit"],
                          [BASE + pid, vid, price, 0])
                    sale_price_of[vid] = price
                    variants.append(vid)
            variants_of_product[pid] = variants

    # ── 소매처 · 거래처 ──────────────────────────────────────────
    partners_of = {}        # 소매처 → [(partner id, 도매처 id)]
    for rid in range(1, r_count + 1):
        shop = f"로드상회 {rid:04d}"
        r.add("retailer", ["id", "email", "password", "shop_name", "approval_status", "approved_at"],
              [BASE + rid, f"load-r{rid:06d}@ondo.test", PASSWORD_HASH, shop, "APPROVED",
               ts(now - timedelta(days=60))])
        r.add("retailer_private", ["retailer_id", "owner_name", "mobile", "biz_reg_no"],
              [BASE + rid, "이로드", f"0108{rid:07d}", f"8{rid:09d}"])

        partners = []
        for k in range(PARTNERS_PER_RETAILER):
            wid = ((rid - 1) * 3 + k) % w_count + 1       # k 가 다르면 도매처가 다르다 — partner_uk
            partner_id = BASE + (rid - 1) * PARTNERS_PER_RETAILER + k + 1
            w.add("partner", ["id", "wholesaler_id", "retailer_id", "retailer_name", "trade_type"],
                  [partner_id, BASE + wid, BASE + rid, shop, "NORMAL"])
            partners.append((partner_id, wid))
        partners_of[rid] = partners

        # 장바구니 — 거래처 상품에서 고른다
        picked = set()
        while len(picked) < CART_ITEMS_PER_RETAILER:
            _, wid = rng.choice(partners)
            picked.add(rng.choice(variants_of_product[rng.choice(products_of[wid])]))
        for i, vid in enumerate(sorted(picked)):
            r.add("cart_item", ["id", "retailer_id", "variant_id", "qty"],
                  [BASE + (rid - 1) * CART_ITEMS_PER_RETAILER + i + 1, BASE + rid, vid, rng.randint(1, 5)])

    # ── 주문 ────────────────────────────────────────────────────
    order_seq = {wid: 0 for wid in range(1, w_count + 1)}
    order_id = item_id = backorder_id = 0

    for gid in range(1, o_count + 1):
        rid = (gid - 1) % r_count + 1
        ordered_at = now - timedelta(minutes=gid * 3)      # 주문서마다 시각이 달라 order_no 가 안 겹친다
        # 셋 중 하나는 도매처 두 곳에 걸친 주문서다. 소매가 도매처별로 쪼개 접수하는 경로
        partners = rng.sample(partners_of[rid], 2 if gid % 3 == 0 else 1)

        total = 0
        for partner_id, wid in partners:
            order_id += 1
            order_seq[wid] += 1
            roll = rng.random()
            status = "NEW" if roll < 0.15 else "CONFIRMED"
            w.add("orders",
                  ["id", "order_number", "retail_order_id", "partner_id", "wholesaler_id", "status",
                   "payment_term", "receive_method", "ordered_at", "confirmed_at"],
                  [BASE + order_id, order_seq[wid], BASE + gid, partner_id, BASE + wid, status,
                   rng.choice(["CASH", "BANK_TRANSFER"]), rng.choice(["AGENT", "RETAILER"]),
                   ts(ordered_at), ts(ordered_at + timedelta(hours=1)) if status == "CONFIRMED" else ""])

            for vid in rng.sample(variants_of_product[rng.choice(products_of[wid])], rng.randint(1, 3)):
                item_id += 1
                qty = rng.randint(1, 10)
                if status == "NEW":
                    allocated = 0
                elif roll < 0.40:
                    allocated = rng.randint(0, qty - 1)        # 덜 받았다 → 나머지가 미송
                else:
                    allocated = qty
                price = sale_price_of[vid]
                w.add("order_item", ["id", "order_id", "variant_id", "qty", "unit_price", "allocated_qty", "shipped_qty"],
                      [BASE + item_id, BASE + order_id, vid, qty, price, allocated, allocated])
                total += qty * price

                if status == "CONFIRMED" and allocated < qty:
                    backorder_id += 1
                    w.add("backorder", ["id", "order_item_id", "qty", "status", "created_at"],
                          [BASE + backorder_id, BASE + item_id, qty - allocated, "OPEN", ts(ordered_at)])

        # order_no 는 앱 형식(YYYYMMDD-HHMM-NNNN)을 따르되 끝을 L+숫자로 둔다.
        # 부하 테스트 중 앱이 오늘 날짜로 채번하는 번호와 절대 안 겹치게 — 겹치면 주문하기가 500 난다
        r.add("order_group", ["id", "retailer_id", "request_id", "order_no", "total_amount", "ordered_at", "status"],
              [BASE + gid, BASE + rid, f"load-{gid}",
               f"{ordered_at.astimezone(KST):%Y%m%d-%H%M}-L{gid:06d}", total, ts(ordered_at), "ACCEPTED"])

    # 다음 주문 번호가 부하 데이터와 안 겹치게 도매처별 연번을 맞춘다
    for row in w.rows["wholesaler"]:
        row[-1] = order_seq[row[0] - BASE]

    out = Path(__file__).parent / "out"
    out.mkdir(exist_ok=True)
    (out / "wholesale.sql").write_text(wholesale_sql(w))
    (out / "retail.sql").write_text(retail_sql(r))

    print(f"[{scale}] 도매처 {w_count} · 상품 {p_count} · 옵션 {len(sale_price_of)} · "
          f"소매처 {r_count} · 주문서 {o_count} · 도매 주문 {order_id} · 주문 항목 {item_id} · 미송 {backorder_id}")
    print(f"→ {out}/wholesale.sql · retail.sql")


class Tables:
    """테이블별 컬럼과 행을 모아 COPY 블록으로 낸다. 넣는 순서를 지키려고 등장 순서를 기억한다."""

    def __init__(self):
        self.columns = {}
        self.rows = {}

    def add(self, table, columns, row):
        if table not in self.columns:
            self.columns[table] = columns
            self.rows[table] = []
        self.rows[table].append(row)

    def copy(self, schema, table):
        buf = io.StringIO()
        writer = csv.writer(buf, lineterminator="\n")
        for row in self.rows.get(table, []):
            writer.writerow(["" if v is None else v for v in row])
        cols = ", ".join(self.columns[table])
        return f"COPY {schema}.{table} ({cols}) FROM STDIN WITH (FORMAT csv, NULL '');\n{buf.getvalue()}\\.\n"


def wholesale_sql(t):
    # 지우는 순서는 FK 역순. 앱이 부하 중에 만든 행(범위 안 id · 범위 안 부모)도 같이 지운다
    return f"""\\set ON_ERROR_STOP on
BEGIN;
DELETE FROM wholesale.backorder     WHERE id >= {BASE} OR order_item_id >= {BASE};
DELETE FROM wholesale.order_item    WHERE id >= {BASE} OR order_id >= {BASE} OR variant_id >= {BASE};
DELETE FROM wholesale.orders        WHERE id >= {BASE} OR partner_id >= {BASE} OR wholesaler_id >= {BASE};
DELETE FROM wholesale.partner       WHERE id >= {BASE} OR wholesaler_id >= {BASE} OR retailer_id >= {BASE};
DELETE FROM wholesale.listing_image WHERE listing_id >= {BASE};
DELETE FROM wholesale.listing_variant WHERE listing_id >= {BASE};
DELETE FROM wholesale.listing       WHERE id >= {BASE};
DELETE FROM wholesale.variant       WHERE product_id >= {BASE};
DELETE FROM wholesale.color_option  WHERE product_id >= {BASE};
DELETE FROM wholesale.product       WHERE id >= {BASE};
DELETE FROM wholesale.wholesaler    WHERE id >= {BASE};

{t.copy('wholesale', 'wholesaler')}
{t.copy('wholesale', 'product')}
{t.copy('wholesale', 'color_option')}
{t.copy('wholesale', 'variant')}
{t.copy('wholesale', 'listing')}
{t.copy('wholesale', 'listing_variant')}
{t.copy('wholesale', 'listing_image')}
{t.copy('wholesale', 'partner')}
{t.copy('wholesale', 'orders')}
{t.copy('wholesale', 'order_item')}
{t.copy('wholesale', 'backorder')}
{setvals('wholesale', ['wholesaler', 'product', 'color_option', 'variant', 'listing', 'listing_image',
                       'partner', 'orders', 'order_item', 'backorder'])}
COMMIT;
ANALYZE wholesale.product, wholesale.variant, wholesale.listing, wholesale.listing_variant,
        wholesale.orders, wholesale.order_item, wholesale.backorder, wholesale.partner;
"""


def retail_sql(t):
    return f"""\\set ON_ERROR_STOP on
BEGIN;
DELETE FROM retail.cart_item        WHERE id >= {BASE} OR retailer_id >= {BASE};
DELETE FROM retail.order_group      WHERE id >= {BASE} OR retailer_id >= {BASE};
DELETE FROM retail.favorite         WHERE retailer_id >= {BASE};
DELETE FROM retail.retailer_doc     WHERE retailer_id >= {BASE};
DELETE FROM retail.retailer_private WHERE retailer_id >= {BASE};
DELETE FROM retail.retailer         WHERE id >= {BASE};

{t.copy('retail', 'retailer')}
{t.copy('retail', 'retailer_private')}
{t.copy('retail', 'cart_item')}
{t.copy('retail', 'order_group')}
{setvals('retail', ['retailer', 'cart_item', 'order_group'])}
COMMIT;
ANALYZE retail.retailer, retail.cart_item, retail.order_group;
"""


def setvals(schema, tables):
    # 부하 데이터를 지운 뒤 다시 넣어도 시퀀스가 뒤로 가지 않게 GREATEST 로 민다
    return "\n".join(
        f"SELECT setval(pg_get_serial_sequence('{schema}.{t}', 'id'), "
        f"GREATEST((SELECT max(id) FROM {schema}.{t}), {BASE}));"
        for t in tables)


def product_name(rng):
    return f"{rng.choice(STYLES)} {rng.choice(ITEMS)}"


def ts(dt):
    return dt.isoformat()


if __name__ == "__main__":
    main()
