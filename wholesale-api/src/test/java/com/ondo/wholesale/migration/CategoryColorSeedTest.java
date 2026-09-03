package com.ondo.wholesale.migration;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카테고리·색상 마스터 시드(V5) 구조 검증 (MUL-90).
 *
 * <p>값(어떤 카테고리·색이 들어가는지)은 팀이 정하는 것이라 여기서 고정하지 않는다.
 * 대신 어떤 값이 들어와도 깨지면 안 되는 구조 규칙을 강제한다 — 트리 정합, 리프 깊이,
 * 그룹 소속, 명시 id INSERT 뒤 채번(setval) 정합.
 */
@SpringBootTest
@Transactional
class CategoryColorSeedTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 카테고리_시드는_3단_트리를_이룬다() {
        Integer broken = jdbc.queryForObject("""
                select count(*) from common.category c
                left join common.category p on p.id = c.parent_id
                where (c.depth = 1 and c.parent_id is not null)
                   or (c.depth = 2 and coalesce(p.depth, -1) <> 1)
                   or (c.depth = 3 and coalesce(p.depth, -1) <> 2)
                """, Integer.class);
        assertThat(broken).isZero();

        List<Map<String, Object>> perDepth = jdbc.queryForList(
                "select depth, count(*) cnt from common.category group by depth order by depth");
        assertThat(perDepth).hasSize(3); // depth 1·2·3 전부 존재
    }

    @Test
    void 중간_노드는_전부_자식이_있다() {
        // 자식 없는 depth1·2 = 상품을 못 다는 죽은 가지
        Integer childless = jdbc.queryForObject("""
                select count(*) from common.category c
                where c.depth < 3
                  and not exists (select 1 from common.category k where k.parent_id = c.id)
                """, Integer.class);
        assertThat(childless).isZero();
    }

    @Test
    void 색상은_전부_그룹에_속하고_hex는_형식을_지킨다() {
        Integer orphan = jdbc.queryForObject("""
                select count(*) from common.color c
                left join common.color_group g on g.id = c.group_id
                where g.id is null
                """, Integer.class);
        assertThat(orphan).isZero();

        Integer emptyGroups = jdbc.queryForObject("""
                select count(*) from common.color_group g
                where not exists (select 1 from common.color c where c.group_id = g.id)
                """, Integer.class);
        assertThat(emptyGroups).isZero();

        Integer badHex = jdbc.queryForObject(
                "select count(*) from common.color where hex is not null and hex !~ '^#[0-9A-F]{6}$'",
                Integer.class);
        assertThat(badHex).isZero();
    }

    @Test
    void 시드_후에도_id_채번이_이어진다() {
        // 명시 id INSERT 뒤 setval 을 안 하면 다음 bigserial 채번이 시드 id 와 충돌한다
        Long categoryId = jdbc.queryForObject(
                "insert into common.category (parent_id, name, depth) values (null, '채번검사', 1) returning id",
                Long.class);
        Long maxSeeded = jdbc.queryForObject(
                "select max(id) from common.category where id <> ?", Long.class, categoryId);
        assertThat(categoryId).isGreaterThan(maxSeeded);

        Long groupId = jdbc.queryForObject(
                "insert into common.color_group (name) values ('채번검사') returning id", Long.class);
        assertThat(groupId).isGreaterThan(jdbc.queryForObject(
                "select max(id) from common.color_group where id <> ?", Long.class, groupId));

        Long colorId = jdbc.queryForObject(
                "insert into common.color (group_id, name) values (?, '채번검사') returning id",
                Long.class, groupId);
        assertThat(colorId).isGreaterThan(jdbc.queryForObject(
                "select max(id) from common.color where id <> ?", Long.class, colorId));
    }
}
