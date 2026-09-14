"""기준선 결과 JSON 을 표 하나로 모은다 (MUL-118). python3 summarize.py small"""
import json
import sys
from pathlib import Path

scale = sys.argv[1] if len(sys.argv) > 1 else "small"
d = Path(__file__).parent / "results" / scale

ACTION_TO_NAME = {
    "listings": "listings", "listingDetail": "listing_detail", "categories": "categories",
    "filterOptions": "filter_options", "cartList": "cart_list", "cartAdd": "cart_add",
    "cartCount": "cart_count", "me": "me", "orderList": "order_list", "orderDetail": "order_detail",
    "backorders": "backorders", "checkout": "checkout", "placeOrder": "place_order",
}
WEIGHT = {"listings": 35, "listing_detail": 20, "categories": 5, "filter_options": 5, "cart_list": 8,
          "cart_add": 5, "cart_count": 5, "me": 5, "order_list": 4, "order_detail": 3,
          "backorders": 2, "checkout": 2, "place_order": 1}
f = lambda x: "-" if x is None else f"{x:.1f}"

out = [f"# 기준선 — {scale}", "", "## 단독 (API 하나만)", "",
       "| API | 비율(가정) | 요청 | p50 | p95 | p99 | max | 실패% |", "|---|---:|---:|---:|---:|---:|---:|---:|"]
single = []
for p in sorted(d.glob("single-*.json")):
    j = json.loads(p.read_text())
    name = ACTION_TO_NAME[j["api"]]
    row = next((r for r in j["rows"] if r["name"] == name), None)
    if row:
        single.append((name, row, j))
single.sort(key=lambda t: -(t[1]["p95"] or 0))
for name, r, j in single:
    out.append(f"| {name} | {WEIGHT[name]}% | {r['count']} | {f(r['p50'])} | {f(r['p95'])} | {f(r['p99'])} | {f(r['max'])} | {r['failRate']*100:.2f} |")
if single:
    out.append(f"\n초당 {single[0][2]['rate']}건. 단위 ms. p95 느린 순.")

out += ["", "## 순위 — 느린 정도 × 사용 빈도", "",
        "p95 × 비율. 사용자가 체감하는 느림의 총량에 가깝다.", "",
        "| 순위 | API | p95 | 비율 | 점수 |", "|---:|---|---:|---:|---:|"]
ranked = sorted(((n, r["p95"] or 0, WEIGHT[n]) for n, r, _ in single), key=lambda t: -t[1] * t[2])
for i, (n, p95, w) in enumerate(ranked, 1):
    out.append(f"| {i} | {n} | {p95:.1f} | {w}% | {p95*w:.0f} |")

out += ["", "## 혼합 (비율대로 섞어서)", ""]
mixed = sorted((json.loads(p.read_text()) for p in d.glob("mixed-*.json")), key=lambda j: j["rate"])
if mixed:
    names = [n for n in WEIGHT if any(any(r["name"] == n for r in j["rows"]) for j in mixed)]
    out.append("p95 (ms) / 실패%")
    out.append("")
    out.append("| API | " + " | ".join(f"{j['rate']}/s" for j in mixed) + " |")
    out.append("|---|" + "---:|" * len(mixed))
    for n in names:
        cells = []
        for j in mixed:
            r = next((r for r in j["rows"] if r["name"] == n), None)
            cells.append("-" if not r else f"{f(r['p95'])} / {r['failRate']*100:.1f}")
        out.append(f"| {n} | " + " | ".join(cells) + " |")
    out.append("| **못 보낸 반복** | " + " | ".join(str(j["dropped"]) for j in mixed) + " |")

(d / "summary.md").write_text("\n".join(out) + "\n")
print("\n".join(out))
