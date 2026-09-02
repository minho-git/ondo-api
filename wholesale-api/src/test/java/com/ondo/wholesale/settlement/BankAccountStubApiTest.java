package com.ondo.wholesale.settlement;

import com.ondo.wholesale.common.error.ErrorResponseWriter;
import com.ondo.wholesale.common.response.ApiResponseBodyAdvice;
import com.ondo.wholesale.common.trace.TraceIdFilter;
import com.ondo.wholesale.config.SecurityConfig;
import com.ondo.wholesale.security.ApprovedAuthorizationManager;
import com.ondo.wholesale.security.RestAccessDeniedHandler;
import com.ondo.wholesale.security.RestAuthenticationEntryPoint;
import com.ondo.wholesale.security.support.TestSecuritySupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정산 계좌 계약 스텁 대표 응답 검증 (MUL-83). */
@WebMvcTest(BankAccountController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        ApprovedAuthorizationManager.class, ErrorResponseWriter.class, ApiResponseBodyAdvice.class,
        TraceIdFilter.class})
class BankAccountStubApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 계좌목록은_주계좌_먼저_페이징없이_내린다() throws Exception {
        mvc.perform(get("/api/wholesale/bank-accounts").with(TestSecuritySupport.approved()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data[0].isPrimary").value(true))
                .andExpect(jsonPath("$.data[1].memo").value((Object) null));
    }

    @Test
    void 계좌등록은_201로_등록된_계좌_한건을_내린다() throws Exception {
        mvc.perform(post("/api/wholesale/bank-accounts")
                        .with(TestSecuritySupport.approved())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bankName": "신한은행", "accountNo": "110-482-948102",
                                  "accountHolder": "서울유통", "isPrimary": true }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountNo").value("110-482-948102"));
    }

    @Test
    void 계좌삭제는_204_본문없음이다() throws Exception {
        mvc.perform(delete("/api/wholesale/bank-accounts/91").with(TestSecuritySupport.approved()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
