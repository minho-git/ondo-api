// API 하나만 잰다 (MUL-118).
//
//   k6 run -e API=listings -e RATE=20 -e DURATION=30s single.js
//
// API 는 common.js 의 actions 이름이다 — listings · listingDetail · cartList · orderList …
// RATE 는 초당 요청 수. 사용자 수가 아니라 도착률을 고정해서, 서버가 느려져도 보내는 양이 안 준다.

import { actions, loginAll, useSession, baseOptions, scenarios, table, SCALE } from './common.js';

const API = __ENV.API || 'listings';
const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '30s';

export const options = baseOptions(scenarios('run', RATE, DURATION));

export function setup() {
  return loginAll();
}

export function run(data) {
  useSession(data);
  actions[API]();
}

export function handleSummary(data) {
  const secs = parseInt(DURATION, 10);
  const { text, rows, dropped } = table(data, undefined, secs);
  const header = `\n[single] API=${API} RATE=${RATE}/s DURATION=${DURATION} SCALE=${SCALE}`;
  return {
    stdout: header + text,
    [`results/single-${SCALE}-${API}-${RATE}.json`]: JSON.stringify({ api: API, rate: RATE, scale: SCALE, dropped, rows }, null, 2),
  };
}
