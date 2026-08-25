# ondo-api

동대문 도매↔소매 B2B. **서버 둘, DB 하나.**

| | |
|---|---|
| `retail-api/` | 소매 API — 민호 |
| `wholesale-api/` | 도매 API — 팀원 |
| `db/` | 스키마 SQL. **여기 하나만 둔다** |

## 로컬에서 띄우기

```bash
docker compose -f db/compose.yml up -d
```

PostgreSQL 16 이 서고 Flyway 가 스키마를 넣는다. `ondo` / `ondo` / `ondo` · 5432.

```bash
cd retail-api && ./gradlew bootRun
```

## 스키마를 바꿀 때

`db/migration/` 에 `V2__...sql` 을 **추가**한다. 이미 적용된 파일은 고치지 않는다 — Flyway 가 막는다.
스키마 변경은 팀원과 상의 후 바꾼다.

스키마는 앱이 아니라 compose 와 배포 파이프라인이 넣는다.
배포 순서가 **스키마 → 도매 → 소매** 라서 어느 앱에도 실으면 안 된다.

## 규칙

- Java 21 · Spring Boot 4.1.1 · Gradle(Groovy)
- `main` 에 직접 푸시하지 않는다. 작업 브랜치 → PR
- **비밀은 커밋하지 않는다.** 환경변수로 주입한다
- 커밋 접두사 — `feat` · `fix` · `refactor` · `test` · `docs` · `chore`
