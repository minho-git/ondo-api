// 비율대로 섞어서 잰다 (MUL-118).
//
//   k6 run -e RATE=50 -e DURATION=60s mixed.js
//
// RATE 는 초당 "행동" 수다. 주문하기처럼 요청을 여러 번 보내는 행동이 있어서 실제 요청은 조금 더 많다.
// 비율은 common.js 의 WEIGHTS — 실사용자가 없어서 정한 가정이다.

import { loginAll, useSession, pickAction, baseOptions, scenarios, table, SCALE } from './common.js';

const RATE = Number(__ENV.RATE || 50);
const DURATION = __ENV.DURATION || '60s';

export const options = baseOptions(scenarios('run', RATE, DURATION));

export function setup() {
  return loginAll();
}

export function run(data) {
  useSession(data);
  pickAction()();
}

export function handleSummary(data) {
  const secs = parseInt(DURATION, 10);
  const { text, rows, dropped } = table(data, undefined, secs);
  const header = `\n[mixed] RATE=${RATE}/s DURATION=${DURATION} SCALE=${SCALE}`;
  return {
    stdout: header + text,
    [`results/mixed-${SCALE}-${RATE}.json`]: JSON.stringify({ rate: RATE, scale: SCALE, dropped, rows }, null, 2),
  };
}
