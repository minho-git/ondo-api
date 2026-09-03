package com.ondo.wholesale.product;

import com.ondo.wholesale.security.support.TestSecuritySupport;
import com.ondo.wholesale.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 카테고리·색상 조회 실구동 검증 (MUL-90) — 스텁이 아니라 V5 시드가 내려와야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryColorApiTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Test
    void 카테고리_트리는_3단으로_내려오고_리프의_children은_빈_배열이다() throws Exception {
        mvc.perform(get("/api/wholesale/categories").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2))) // 여성·남성
                .andExpect(jsonPath("$.data[0].name").value("여성"))
                .andExpect(jsonPath("$.data[0].children", hasSize(8))) // 대분류 8종
                .andExpect(jsonPath("$.data[0].children[1].id").value(12)) // 상의
                .andExpect(jsonPath("$.data[0].children[1].children", hasSize(4)))
                .andExpect(jsonPath("$.data[0].children[1].children[0].name").value("티셔츠"))
                .andExpect(jsonPath("$.data[0].children[1].children[0].depth").value(3))
                .andExpect(jsonPath("$.data[0].children[1].children[0].children", hasSize(0)))
                .andExpect(jsonPath("$.data[1].name").value("남성"))
                .andExpect(jsonPath("$.data[1].children", hasSize(5)))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void 색상은_그룹과_그룹내_정렬순으로_내려온다() throws Exception {
        mvc.perform(get("/api/wholesale/colors").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6))) // 그룹 6종
                .andExpect(jsonPath("$.data[0].name").value("무채색"))
                .andExpect(jsonPath("$.data[0].colors", hasSize(6)))
                .andExpect(jsonPath("$.data[0].colors[0].name").value("블랙"))
                .andExpect(jsonPath("$.data[0].colors[0].hex").value("#191F28"))
                .andExpect(jsonPath("$.data[3].name").value("데님 워싱"))
                .andExpect(jsonPath("$.data[5].colors[1].name").value("실버"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void 미승인_계정은_카테고리를_못_본다() throws Exception {
        mvc.perform(get("/api/wholesale/categories").with(TestSecuritySupport.pending()))
                .andExpect(status().isForbidden());
    }
}
