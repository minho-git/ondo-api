// 소매 부하 테스트 공통 부품 (MUL-118).
//
// 사용자 행동 하나 = 함수 하나. 요청마다 name 태그를 붙여 API 별로 수치를 가른다.
// 행동 하나가 요청을 여러 번 보낼 수 있다(주문하기 = 담기 → 장바구니 → 주문).
// 그래서 비율은 "행동" 기준이고, API 별 요청 수는 그와 조금 다르다.

import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// data/generate.py 의 규모와 맞춘다. 부하 데이터 id 는 1,000,000 부터다
const SCALES = {
  small:  { products: 1_000,   retailers: 100,   orders: 1_000 },
  medium: { products: 10_000,  retailers: 500,   orders: 10_000 },
  large:  { products: 100_000, retailers: 1_000, orders: 100_000 },
};
export const SCALE = __ENV.SCALE || 'small';
const S = SCALES[SCALE];
const ID = 1_000_000;

const LEAF_CATEGORIES = [111, 112, 113, 114, 115, 116, 121, 122, 123, 124, 131, 132, 141, 142, 143, 144,
  151, 152, 153, 154, 155, 156, 157, 161, 162, 163, 164, 165, 171, 172, 173, 181, 182, 183,
  211, 212, 213, 214, 215, 216, 221, 222, 223, 224, 225, 231, 232, 233, 234, 235, 241, 242, 243, 244, 251, 252];
const VARIANTS_PER_PRODUCT = 6;

// 로그인은 측정 전에 끝낸다 — setup() 에서 소매처마다 한 번씩만 하고 세션을 받아 둔다.
//
// 처음엔 VU 마다 첫 반복에서 로그인했다. 그런데 도착률을 고정하면 서버가 느려질 때 k6 가
// VU 를 더 만들고, 새 VU 가 또 로그인한다. 로그인은 BCrypt 대조라 CPU 를 많이 먹어서
// 서버가 더 느려지고 VU 가 더 늘어나는 악순환이 됐다 — 초당 400 에서 로그인 2,000 번.
// 그 결과로는 API 가 느린 건지 로그인 폭주 탓인지 가를 수 없어서 측정에서 뺐다.
const SESSION_COOKIE = __ENV.SESSION_COOKIE || 'SESSION_RETAIL';

export function loginAll() {
  const sessions = [];
  for (let start = 1; start <= S.retailers; start += 20) {
    const batch = [];
    for (let no = start; no < start + 20 && no <= S.retailers; no++) {
      batch.push(['POST', `${BASE_URL}/api/retail/auth/login`,
        JSON.stringify({ email: `load-r${String(no).padStart(6, '0')}@ondo.test`, password: 'ondo1234!' }),
        // 로그인마다 빈 쿠키 통을 쓴다. 같은 통을 쓰면 앞 로그인의 세션 쿠키를 들고 가서
        // 서버가 새 쿠키를 안 준다 — 이 테스트를 짜다 소매 로그인의 세션 고정 버그를 찾았다
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_login' }, jar: new http.CookieJar() }]);
    }
    for (const res of http.batch(batch)) {
      const cookie = res.cookies[SESSION_COOKIE];
      if (res.status !== 200 || !cookie || !cookie.length) {
        throw new Error(`로그인 실패 ${res.status} — 데이터를 넣었나? (data/load.sh ${SCALE})`);
      }
      sessions.push(cookie[0].value);
    }
  }
  return { sessions };
}

// VU 마다 한 번 — 받아 둔 세션 하나를 쿠키 통에 넣는다.
// VU 가 소매처 수보다 많으면 한 세션을 여럿이 나눠 쓴다. 조회 위주라 문제없다
let retailerNo = 0;
let cartItemIds = [];

export function useSession(data) {
  if (retailerNo) return;
  retailerNo = ((__VU - 1) % S.retailers) + 1;
  http.cookieJar().set(BASE_URL, SESSION_COOKIE, data.sessions[retailerNo - 1]);
}

function get(name, path) {
  const res = http.get(`${BASE_URL}${path}`, { tags: { name } });
  check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

function post(name, path, body, headers = {}) {
  const res = http.post(`${BASE_URL}${path}`, JSON.stringify(body),
    { headers: { 'Content-Type': 'application/json', ...headers }, tags: { name } });
  check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

const rand = (n) => Math.floor(Math.random() * n);
const pick = (arr) => arr[rand(arr.length)];
const randomVariant = () => ID + rand(S.products) * VARIANTS_PER_PRODUCT + rand(VARIANTS_PER_PRODUCT) + 1;

// ── 행동 ──────────────────────────────────────────────────────

export const actions = {
  // 대부분 앞쪽 페이지를 본다. 30% 는 카테고리를 걸고 본다
  listings() {
    const category = Math.random() < 0.3 ? `&categoryId=${pick(LEAF_CATEGORIES)}` : '';
    get('listings', `/api/retail/listings?page=${rand(5)}&size=20${category}`);
  },

  listingDetail() {
    get('listing_detail', `/api/retail/listings/${ID + rand(S.products) + 1}`);
  },

  categories() {
    get('categories', '/api/retail/categories');
  },

  filterOptions() {
    get('filter_options', '/api/retail/filter-options');
  },

  cartList() {
    const res = get('cart_list', '/api/retail/cart-items');
    cartItemIds = extractCartItemIds(res);
  },

  cartAdd() {
    post('cart_add', '/api/retail/cart-items', { variantId: randomVariant(), qty: 1 });
  },

  cartCount() {
    get('cart_count', '/api/retail/cart-items/count');
  },

  me() {
    get('me', '/api/retail/auth/me');
  },

  orderList() {
    get('order_list', '/api/retail/orders?page=0&size=20');
  },

  // 자기 주문서만 연다. generate.py 는 주문서를 소매처에 돌아가며 나눠줬다 — (gid-1) % R + 1
  orderDetail() {
    const perRetailer = Math.floor(S.orders / S.retailers);
    const gid = retailerNo + S.retailers * rand(perRetailer);
    get('order_detail', `/api/retail/orders/${ID + gid}`);
  },

  backorders() {
    get('backorders', '/api/retail/backorders');
  },

  // 주문서는 장바구니 id 가 필요하다. 직전에 본 장바구니를 쓰고, 없으면 먼저 본다
  checkout() {
    if (cartItemIds.length === 0) actions.cartList();
    if (cartItemIds.length === 0) return;
    get('checkout', `/api/retail/checkout?cartItemIds=${cartItemIds.slice(0, 5).join(',')}`);
  },

  // 실제 흐름대로 한다 — 주문하면 그 장바구니 항목이 지워지므로 고정 id 로는 못 한다
  placeOrder() {
    const variantId = randomVariant();
    post('cart_add', '/api/retail/cart-items', { variantId, qty: 1 });

    const cart = get('cart_list', '/api/retail/cart-items');
    const line = findCartLine(cart, variantId);
    if (!line) return;

    post('place_order', '/api/retail/orders', {
      cartItemIds: [line.cartItemId],
      wholesalerOptions: [{ wholesalerId: line.wholesalerId, paymentTerm: 'CASH', receiveMethod: 'AGENT' }],
    }, { 'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}` });
    cartItemIds = [];
  },
};

// 앞에서 정한 비율 (가정). 실사용자가 없어서 새벽 사입 흐름을 기준으로 정했다
export const WEIGHTS = [
  ['listings', 35], ['listingDetail', 20], ['categories', 5], ['filterOptions', 5],
  ['cartList', 8], ['cartAdd', 5], ['cartCount', 5], ['me', 5],
  ['orderList', 4], ['orderDetail', 3], ['backorders', 2], ['checkout', 2], ['placeOrder', 1],
];

export function pickAction() {
  const total = WEIGHTS.reduce((s, [, w]) => s + w, 0);
  let roll = Math.random() * total;
  for (const [name, w] of WEIGHTS) {
    if ((roll -= w) < 0) return actions[name];
  }
  return actions.listings;
}

// ── 응답 읽기 ─────────────────────────────────────────────────

function cartLines(res) {
  if (res.status !== 200) return [];
  const data = res.json('data');
  const groups = (data && (data.groups || data.wholesalers)) || [];
  return groups.flatMap((g) => (g.items || []).map((i) => ({
    cartItemId: i.cartItemId ?? i.id,
    variantId: i.variantId,
    wholesalerId: g.wholesalerId ?? (g.wholesaler && g.wholesaler.id),
  })));
}

function extractCartItemIds(res) {
  return cartLines(res).map((l) => l.cartItemId);
}

function findCartLine(res, variantId) {
  return cartLines(res).find((l) => l.variantId === variantId);
}

// ── 요약 ──────────────────────────────────────────────────────

export const REQUEST_NAMES = ['listings', 'listing_detail', 'categories', 'filter_options',
  'cart_list', 'cart_add', 'cart_count', 'me', 'order_list', 'order_detail', 'backorders', 'checkout', 'place_order'];

// k6 는 태그별 수치를 요약에 안 보여준다. 태그마다 느슨한 임계값을 걸면 하위 지표로 잡혀서 보인다.
// 워밍업 구간은 빼고 본다 — phase:measure 만 센다
export function perNameThresholds(names = REQUEST_NAMES) {
  const t = {};
  for (const n of names) {
    t[`http_req_duration{name:${n},phase:measure}`] = ['max>=0'];
    t[`http_req_failed{name:${n},phase:measure}`] = ['rate>=0'];
  }
  return t;
}

// 두 실행 스크립트가 같이 쓰는 옵션.
//
// noCookiesReset — k6 는 기본으로 반복이 끝날 때마다 쿠키를 지운다. 그러면 첫 반복에서 받은
// 세션이 다음 반복부터 사라져 401 이 난다. 실제 사용자는 세션을 유지하므로 끈다.
export function baseOptions(scenarioDefs) {
  return {
    scenarios: scenarioDefs,
    thresholds: perNameThresholds(),
    noCookiesReset: true,
    setupTimeout: '5m',
    summaryTrendStats: ['count', 'med', 'p(95)', 'p(99)', 'max'],
  };
}

// 워밍업 → 측정. JIT 이 덜 데워진 첫 몇 초가 p99 를 망치지 않게 앞을 버린다
export function scenarios(exec, rate, duration, warmup = '10s') {
  const common = {
    executor: 'constant-arrival-rate', exec, rate, timeUnit: '1s',
    preAllocatedVUs: Math.max(10, rate), maxVUs: Math.max(50, rate * 5),
  };
  return {
    warmup:  { ...common, duration: warmup, tags: { phase: 'warmup' } },
    measure: { ...common, duration, startTime: warmup, tags: { phase: 'measure' } },
  };
}

export function table(data, names = REQUEST_NAMES, measureSeconds) {
  const rows = [];
  for (const n of names) {
    const d = data.metrics[`http_req_duration{name:${n},phase:measure}`];
    const f = data.metrics[`http_req_failed{name:${n},phase:measure}`];
    if (!d || !d.values || !d.values.count) continue;
    const v = d.values;
    rows.push({ name: n, count: v.count, p50: v.med, p95: v['p(95)'], p99: v['p(99)'], max: v.max,
      failRate: f ? f.values.rate : 0 });
  }
  const dropped = data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0;
  const fmt = (x) => (x === undefined ? '-' : x.toFixed(1)).padStart(8);
  const lines = [
    '',
    `${'API'.padEnd(16)}${'요청'.padStart(8)}${'RPS'.padStart(8)}${'p50'.padStart(8)}${'p95'.padStart(8)}${'p99'.padStart(8)}${'max'.padStart(8)}${'실패%'.padStart(8)}`,
    ...rows.map((r) => `${r.name.padEnd(16)}${String(r.count).padStart(8)}${(r.count / measureSeconds).toFixed(1).padStart(8)}` +
      `${fmt(r.p50)}${fmt(r.p95)}${fmt(r.p99)}${fmt(r.max)}${(r.failRate * 100).toFixed(2).padStart(8)}`),
    `(단위 ms · 측정 ${measureSeconds}s · 못 보낸 반복 ${dropped} — 0 이 아니면 k6 쪽 VU 가 모자랐다)`, '',
  ];
  return { text: lines.join('\n'), rows, dropped };
}
