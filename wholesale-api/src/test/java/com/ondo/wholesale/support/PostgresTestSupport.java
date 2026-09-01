package com.ondo.wholesale.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DB 가 필요한 테스트의 바닥.
 *
 * <p>빈 postgres:16 컨테이너를 띄우고 그 위에서 Flyway 가 V1 부터 순서대로 돈다.
 * 개발자 로컬 compose DB(localhost:5432) 를 보지 않는다 — 로컬에 남은 데이터나
 * 이미 적용된 마이그레이션에 결과가 좌우되지 않고, 도커만 있으면 CI 에서도 그대로 돈다.
 *
 * <p>컨테이너는 static 이라 JVM 당 한 번만 뜬다. 테스트 클래스마다 다시 띄우지 않는다.
 * 정리는 Testcontainers 의 Ryuk 이 맡는다.
 */
public abstract class PostgresTestSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }
}
