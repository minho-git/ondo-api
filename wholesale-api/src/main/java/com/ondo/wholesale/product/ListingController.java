package com.ondo.wholesale.product;

import com.ondo.wholesale.product.domain.ListingStatus;
import com.ondo.wholesale.product.dto.ListingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 상태 전이 계약 스텁 (MUL-81) — 원본: api-lite/02_상품게시.
 * 게시글은 자기 CRUD 경로가 없다 — 생성·수정은 상품 PATCH 가 흡수하고, 여기는 시즌 전이 둘뿐.
 */
@Tag(name = "02 상품·게시")
@RestController
@RequestMapping("/api/wholesale/listings/{listingId}")
public class ListingController {

    @Operation(summary = "시즌 종료 (ON_SALE → SEASON_ENDED)", description = """
            게시글을 마켓에서 내리는 유일한 수단. 상품·재고·주문·판매가는 전부 유지되고 재개로 되돌릴 수 있다.

            에러: 404 `RESOURCE_NOT_FOUND` / 409 `TRANSITION_NOT_ALLOWED` (이미 SEASON_ENDED)""")
    @PostMapping("/season-end")
    public ListingResponse seasonEnd(@PathVariable Long listingId) {
        return ProductStubExamples.listing(ListingStatus.SEASON_ENDED);
    }

    @Operation(summary = "시즌 재개 (SEASON_ENDED → ON_SALE)", description = """
            판매가·이미지가 그대로 살아난다. `seasonStartedAt`은 재개 시각으로 갱신된다.

            에러: 404 `RESOURCE_NOT_FOUND` / 409 `TRANSITION_NOT_ALLOWED` (이미 ON_SALE)""")
    @PostMapping("/reopen")
    public ListingResponse reopen(@PathVariable Long listingId) {
        return ProductStubExamples.listing(ListingStatus.ON_SALE);
    }
}
