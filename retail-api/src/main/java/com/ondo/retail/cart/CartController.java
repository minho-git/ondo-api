package com.ondo.retail.cart;

import com.ondo.retail.cart.dto.AddCartItemRequest;
import com.ondo.retail.cart.dto.AddCartItemResponse;
import com.ondo.retail.cart.dto.CartCountResponse;
import com.ondo.retail.cart.dto.CartResponse;
import com.ondo.retail.cart.dto.ChangeQtyRequest;
import com.ondo.retail.cart.dto.ChangeQtyResponse;
import com.ondo.retail.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니. 승인된 소매처만 부를 수 있다 — 검사는 SecurityConfig 가 앞에서 한다.
 *
 * <p>소매처 id 는 <b>세션에서만</b> 꺼낸다. 요청에 담긴 값을 믿으면 남의 장바구니를 볼 수 있다.
 */
@Tag(name = "장바구니", description = "여러 도매처 상품을 한 바구니에 담는다. 주문할 때 도매처별로 쪼개진다.")
@RestController
@RequestMapping("/api/retail/cart-items")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 담기. 이미 담은 옵션이면 수량을 더한다. */
    @Operation(summary = "장바구니 담기",
               description = "같은 SKU 를 다시 담으면 수량이 더해진다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddCartItemResponse> add(@RequestBody @Valid AddCartItemRequest request,
                                                Authentication authentication) {
        return ApiResponse.of(cartService.add(retailerId(authentication), request));
    }

    /** 조회. 도매처별로 묶어서 내린다. */
    @Operation(summary = "장바구니 조회",
               description = "도매처별로 묶어서 내린다. 주문서와 같은 모양이다.")
    @GetMapping
    public ApiResponse<CartResponse> find(Authentication authentication) {
        return ApiResponse.of(cartService.find(retailerId(authentication)));
    }

    /** 헤더 뱃지용. 15개 화면 전부에서 부른다. */
    @Operation(summary = "담긴 개수",
               description = "헤더의 뱃지 숫자. 자주 부르는 자리라 따로 뒀다.")
    @GetMapping("/count")
    public ApiResponse<CartCountResponse> count(Authentication authentication) {
        return ApiResponse.of(new CartCountResponse(cartService.count(retailerId(authentication))));
    }

    /** 수량 변경. 그 값으로 교체한다. 바뀐 줄 금액을 같이 돌려준다. */
    @Operation(summary = "수량 변경",
               description = "더하는 게 아니라 그 값으로 교체한다. 바뀐 줄 금액이 같이 온다.")
    @PatchMapping("/{cartItemId}")
    public ApiResponse<ChangeQtyResponse> changeQty(@PathVariable Long cartItemId,
                                                    @RequestBody @Valid ChangeQtyRequest request,
                                                    Authentication authentication) {
        return ApiResponse.of(
                cartService.changeQty(retailerId(authentication), cartItemId, request.qty()));
    }

    /**
     * 빼기. 단건만 — 선택 삭제는 프론트가 여러 번 부른다.
     *
     * <p>본문 없이 204 다. <b>없는 id 여도 204</b> — 결과가 같으면 같은 응답을 낸다.
     */
    @Operation(summary = "장바구니 빼기",
               description = "본문 없이 204 다. 없는 id 여도 204 — 결과가 같으면 같은 응답을 낸다.")
    @DeleteMapping("/{cartItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long cartItemId, Authentication authentication) {
        cartService.remove(retailerId(authentication), cartItemId);
    }

    private static Long retailerId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
