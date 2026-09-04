package com.ondo.retail.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondo.retail.listing.ListingClient;
import com.ondo.retail.listing.dto.VariantInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니의 HTTP 계약. 명세와 어긋나기 쉬운 자리만 잡는다.
 *
 * <p>수량 변경이 <b>줄 금액을 돌려주는지</b>와 빼기가 <b>없는 id 에도 204 인지</b>가 핵심이다.
 * 둘 다 명세에 적혀 있는데 코드가 다르게 굴던 자리다.
 *
 * <p>{@code @WithMockUser(username = "1")} — 컨트롤러가 Authentication 의 이름을
 * retailerId 로 읽는다. 시드의 봄봄상회가 1 번이다.
 *
 * <p>도매는 가짜로 세운다 (MUL-88). 상품 값은 전부 도매 것이라 장바구니가 줄을 그리려면
 * 도매를 부르는데, 여기서 확인할 건 장바구니의 HTTP 계약이지 도매 연동이 아니다.
 * 진짜로 부르게 두면 도매 서버가 떠 있어야만 통과하는 테스트가 된다 —
 * 도매 연동은 {@code WholesaleListingAdapterTest} 가 따로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "1")
class CartControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ListingClient listingClient;

    /** 값은 로컬 시드의 3001(체리레드 S)과 같게 뒀다. 읽기 편하라고 맞춘 것이고 시드에 기대지는 않는다. */
    private static final VariantInfo 셔츠_S = new VariantInfo(
            3001L, 2001L, "빈티지 플라워 셔츠", "cover.jpg", "체리레드", "S",
            12500, 500, 101L, "무드온", true);

    @BeforeEach
    void 가짜_도매를_세운다() {
        Mockito.when(listingClient.findVariants(Mockito.anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            Map<Long, VariantInfo> found = new LinkedHashMap<>();
            for (Long id : ids) {
                if (셔츠_S.variantId().equals(id)) {
                    found.put(id, 셔츠_S);
                }
            }
            return found;
        });
    }

    private Long 담기(long variantId, int qty) throws Exception {
        String body = mvc.perform(post("/api/retail/cart-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":%d,\"qty\":%d}".formatted(variantId, qty)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"cartItemId\":(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("수량을 바꾸면 바뀐 줄 금액이 같이 온다")
    void 수량_변경_응답() throws Exception {
        Long cartItemId = 담기(3001L, 3);

        mvc.perform(patch("/api/retail/cart-items/" + cartItemId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qty\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartItemId").value(cartItemId))
                .andExpect(jsonPath("$.data.qty").value(5))
                .andExpect(jsonPath("$.data.lineAmount").value(62500));
    }

    @Test
    @DisplayName("빼기는 204 다 — 없는 id 를 또 빼도 204 다")
    void 빼기는_멱등() throws Exception {
        Long cartItemId = 담기(3001L, 1);

        mvc.perform(delete("/api/retail/cart-items/" + cartItemId))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/retail/cart-items/" + cartItemId))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/retail/cart-items/999999"))
                .andExpect(status().isNoContent());
    }
}
