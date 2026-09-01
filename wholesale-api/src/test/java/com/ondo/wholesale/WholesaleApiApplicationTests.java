package com.ondo.wholesale;

import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 빈 DB 에 마이그레이션이 다 돈 뒤 ddl-auto: validate 로 앱이 뜨는지 본다 (MUL-67 AC 2).
 */
@SpringBootTest
class WholesaleApiApplicationTests extends PostgresTestSupport {

    @Test
    void contextLoads() {
    }

}
