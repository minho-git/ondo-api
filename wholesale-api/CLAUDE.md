# wholesale-api — 도매 API

온도 ERP 도매 API. 공통 규칙은 [루트 CLAUDE.md](../CLAUDE.md) 를 따른다. 여기엔 도매 고유 사항만.

## 좌표

- 포트 **8081**
- DB `ondo_wholesale` @ `localhost:5432` (로컬, 계정 `ondo/ondo`)
- 기본 스키마 **`wholesale`** · 세션 테이블 `wholesale.spring_session`
- 베이스 패키지 `com.ondo.wholesale`
- 진입점 `WholesaleApiApplication`

## 명령어

```bash
./gradlew bootRun     # 로컬 실행 (default 프로파일 = local)
./gradlew test        # 테스트
./gradlew build       # 빌드
```

## 스키마 / 마이그레이션

- 위치: `src/main/resources/db/migration/` — `V2__...sql` 처럼 **새 파일을 추가**한다.
- **이미 적용된 파일은 고치지 않는다.** Flyway 가 막는다.
- `flyway.default-schema = wholesale` — 이력 테이블도 여기 생긴다.
- JPA 는 `ddl-auto: validate` — 엔티티가 실제 스키마와 어긋나면 **앱이 안 뜬다.** 엔티티를 바꾸면 마이그레이션도 같이.
- 기본 스키마는 `wholesale`. `common` 스키마 테이블을 쓸 땐 엔티티에 `@Table(schema = "common")` 을 명시한다.

## 주의

- 소매 DB 를 직접 보지 않는다. 소매 연동은 retail-api 호출로.
