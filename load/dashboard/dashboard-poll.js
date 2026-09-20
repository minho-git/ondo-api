// 대시보드 폴링 부하 — 도매처가 화면을 켜두면 30초마다 자동 갱신된다.
//
//   k6 run -e SHOPS=200 -e DURATION=60s dashboard-poll.js
//
// 도매처 SHOPS 곳이 각각 30초 간격으로 부르면 초당 SHOPS/30 건이 들어온다.
// 세션은 setup 에서 한 번만 만들고, 요청마다 무작위로 하나를 쓴다.

import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8099';
// 비밀번호는 커밋하지 않는다 — 시드로 넣은 계정의 값을 실행할 때 준다.
//   k6 run -e PASSWORD=... dashboard-poll.js
const PASSWORD = __ENV.PASSWORD;
const SHOPS = Number(__ENV.SHOPS || 200);       // 화면을 켜둔 도매처 수
const POLL_SEC = Number(__ENV.POLL || 30);      // 갱신 주기
// 세션 수 = 서로 다른 도매처 수. 이게 중요하다 — 도매처가 다르면 요약도 따로 갱신된다.
// 세션을 적게 두면 같은 요약만 계속 읽어서 재계산 부하가 측정에서 사라진다.
const SESSIONS = Number(__ENV.SESSIONS || Math.min(SHOPS, 50));

export const options = {
    scenarios: {
        polling: {
            executor: 'constant-arrival-rate',
            rate: Math.max(1, Math.round(SHOPS / POLL_SEC)),
            timeUnit: '1s',
            duration: __ENV.DURATION || '60s',
            preAllocatedVUs: 50,
            maxVUs: 300,
        },
    },
    thresholds: {
        'http_req_duration{name:dashboard}': ['p(95)<500'],
        'http_req_failed': ['rate<0.01'],
    },
};

export function setup() {
    if (!PASSWORD) throw new Error('PASSWORD 를 줘야 한다 — k6 run -e PASSWORD=... dashboard-poll.js');
    const cookies = [];
    for (let i = 1; i <= SESSIONS; i++) {
        const res = http.post(`${BASE}/api/wholesale/auth/login`,
            JSON.stringify({ email: `perf${i}@ondo.test`, password: PASSWORD }),
            { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' },
              jar: new http.CookieJar() });   // 로그인마다 새 쿠키통 — 안 그러면 직전 세션이 무효화된다
        const setCookie = res.headers['Set-Cookie'];
        if (setCookie) {
            const m = String(setCookie).match(/SESSION_WHOLESALE=([^;]+)/);
            if (m) cookies.push(m[1]);
        }
    }
    console.log('세션 수', cookies.length);
    if (!cookies.length) throw new Error('로그인 세션을 하나도 못 만들었다');
    return { cookies };
}

export default function (data) {
    const cookie = data.cookies[Math.floor(Math.random() * data.cookies.length)];
    const res = http.get(`${BASE}/api/wholesale/dashboard/summary`, {
        headers: { Cookie: `SESSION_WHOLESALE=${cookie}` },
        tags: { name: 'dashboard' },
    });
    if (res.status !== 200 && __ITER < 3) console.log('실패', res.status, String(res.body).slice(0, 120));
    check(res, { '200': r => r.status === 200 });
}
