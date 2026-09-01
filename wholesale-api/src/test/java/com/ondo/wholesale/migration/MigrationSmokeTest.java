package com.ondo.wholesale.migration;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빈 DB 에서 마이그레이션 전체가 순서대로 통과하는지 본다 (MUL-67 AC 1).
 *
 * <p>여기서 잡고 싶은 사고는 "오늘 추가한 V2 가 붙나" 가 아니다. 마이그레이션은 쌓이는데
 * 개발자 로컬 DB 는 늘 "이미 다 적용된 상태" 라서 새 파일 한 장만 검증된다. V1 부터
 * 처음부터 도는 경로는 실제로 배포 때 한 번 실행되고, 거기서 처음 깨지면 그건 사고다.
 */
@SpringBootTest
class MigrationSmokeTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void 테스트는_로컬_DB가_아니라_Testcontainers_컨테이너를_본다() throws SQLException {
        String url = dataSource.getConnection().getMetaData().getURL();

        // 로컬 compose DB(5432) 에 조용히 붙어버리면 아래 검증이 전부 무의미해진다
        assertThat(url).contains(String.valueOf(POSTGRES.getFirstMappedPort()));
    }

    @Test
    void 빈_DB에서_마이그레이션이_전부_성공한다() {
        List<Map<String, Object>> history = jdbc.queryForList(
                "select version, description, success"
                        + " from wholesale.flyway_schema_history"
                        + " order by installed_rank");

        assertThat(history).isNotEmpty();
        assertThat(history).allSatisfy(row ->
                assertThat(row.get("success")).as("%s 마이그레이션", row.get("description")).isEqualTo(true));
    }

    @Test
    void db_migration_의_모든_버전_파일이_적용된다() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql");

        Integer applied = jdbc.queryForObject(
                "select count(*) from wholesale.flyway_schema_history where version is not null",
                Integer.class);

        // 파일을 추가하고 돌리지 않은 채 넘어가는 걸 막는다
        assertThat(applied).isEqualTo(files.length);
    }
}
