package com.ondo.retail.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
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
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "1")
class CartControllerTest {

    @Autowired MockMvc mvc;

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
        Long cartItemId = 담기(90231L, 3);

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
        Long cartItemId = 담기(90231L, 1);

        mvc.perform(delete("/api/retail/cart-items/" + cartItemId))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/retail/cart-items/" + cartItemId))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/retail/cart-items/999999"))
                .andExpect(status().isNoContent());
    }
}
