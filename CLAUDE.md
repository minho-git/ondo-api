# ondo-api

동대문 도매↔소매 B2B. **서버 둘 · DB 둘**인 모노레포다.

| 폴더 | 내용 | 하위 규칙 |
|---|---|---|
| `wholesale-api/` | 도매 API (포트 8081) | [wholesale-api/CLAUDE.md](wholesale-api/CLAUDE.md) |
| `retail-api/` | 소매 API (포트 8080) | [retail-api/CLAUDE.md](retail-api/CLAUDE.md) |
| `db/` | 로컬 PostgreSQL 두 대 compose | — |

**한 프로젝트만 손볼 때는 그 폴더에서 세션을 시작해라.** 루트에서 시작하면 도매·소매 규칙이 섞인다.

## 공통 스택

- Java 21 · Spring Boot 4.1.1 · Gradle(Groovy)
- PostgreSQL 16 · Flyway · Spring Data JPA · Spring Security · Spring Session(JDBC)
- Lombok · Bean Validation
- 두 프로젝트는 **독립 Gradle 프로젝트**다 (각자 `settings.gradle`, `gradlew`). 루트에 통합 빌드 없음.

## 로컬에서 띄우기

```bash
docker compose -f db/compose.yml up -d      # DB 두 대 (도매 5432 / 소매 5433, 계정 ondo/ondo)
cd wholesale-api && ./gradlew bootRun        # 8081
cd retail-api    && ./gradlew bootRun        # 8080
```

앱이 뜰 때 **Flyway 가 자기 스키마를 넣는다.** 빈 DB 로 시작해도 된다. compose 를 내려도(`down`) 데이터는 남는다. 지우려면 `down -v`.

## 규칙

- **`main` 직접 푸시 금지.** `dev` 에서 피처 브랜치를 따고 `dev` 로 PR.
- **브랜치** — `<타입>/MUL-<번호>-<짧은설명>` · 예: `feat/MUL-123-order-confirm-api`
- **커밋** — `<타입>: MUL-<번호> <설명>` · 예: `feat: MUL-123 주문 확정 API 구현`
- **PR 제목** — `[MUL-<번호>] <설명>` · 예: `[MUL-123] 주문 확정 API 구현` · base 는 `dev`
- 타입 어휘는 셋 다 공통 — `feat` · `fix` · `refactor` · `test` · `docs` · `chore`
- `MUL` 은 지라 프로젝트 키다. **지라 티켓이 없는 작업(초기 세팅 등)은 키를 생략**한다.
- **비밀은 커밋하지 않는다.** 환경변수로 주입. (compose 의 `ondo/ondo` 는 로컬 전용, 배포용 아님)
- `docs-local/`, `agent-local/` 은 gitignore 됨 — 개인 문서·에이전트용, 올리지 않는다.

## Jira 연동

지라 이슈 조회·연결은 **Atlassian MCP**(프로젝트 `.mcp.json` 에 등록됨)로 한다.
각자 **자기 Atlassian 계정으로 OAuth 인증**한다 — 공용 토큰·`.env` 없음. 클론 후 최초 1회 Claude Code 에서 `/mcp` 로 로그인한다.

**티켓 키(예: `MUL-123`)를 받으면 이 순서로 작업한다:**

1. 해당 지라 티켓을 조회한다.
2. 요구사항과 acceptance criteria 를 확인한다.
3. 관련 코드를 탐색한다.
4. 구현 계획을 세운다.
5. 코드를 수정한다.
6. 테스트를 실행한다.
7. 완료 후 변경 내용을 요약한다.
8. **지라 상태 변경·댓글 작성은 사용자 확인을 받은 뒤** 수행한다.

- 티켓에 명시되지 않은 요구사항을 임의로 추가하지 않는다.
- 요구사항이 모호하면 진행하지 말고 **사용자에게 질문**한다.

## DB 두 대 구조 (중요)

도매·소매는 **서로 다른 물리 DB**다. 한 트랜잭션·조인으로 두 DB 를 묶을 수 없다. 서버 간 연동은 API 호출로 한다.
스키마 변경 규칙은 각 프로젝트의 CLAUDE.md 를 봐라.
