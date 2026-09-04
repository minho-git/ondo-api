package com.ondo.wholesale.retailgateway;

import com.ondo.wholesale.common.error.ApiException;
import com.ondo.wholesale.common.error.ErrorCode;
import com.ondo.wholesale.common.response.ApiResponse;
import com.ondo.wholesale.retailgateway.dto.RetailBackorderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 소매 미송 조회 (MUL-97) — 소매 백엔드가 부른다.
 *
 * <p>{@code /api/retail-gateway/**} 는 MUL-87 의 공유 시크릿으로 잠겨 있다
 * ({@link GatewaySecretAuthorizationManager}). 소매 백엔드만 들어온다.
 *
 * <p><b>상품과 달리 {@code retailerId} 를 받는다.</b> 미송은 소매처별로 잘린 데이터라
 * 안 받으면 남의 주문이 보인다. 시크릿은 "소매 서버가 맞다" 까지만 증명하고
 * "누구의 미송이냐" 는 소매가 자기 세션에서 꺼내 실어 보낸다. 주문 접수
 * ({@link RetailGatewayOrderController})가 {@code retailerId} 를 싣는 것과 같은 방식이다.
 *
 * <p>채빈의 {@code BackorderController} 와 겹치지 않는다. 그쪽은 도매 화면용이라
 * SKU 로 묶어 배분 입력을 받는다. 여기는 소매 화면용이라 소매처가 뭘 언제 받는지만 본다.
 */
@Tag(name = "08 소매접점")
@RestController
@RequestMapping("/api/retail-gateway")
@RequiredArgsConstructor
public class RetailGatewayBackorderController {

    /** 한 번에 가져갈 수 있는 최대. 소매도 같은 값으로 막지만 여기가 최종 방어다. */
    private static final int MAX_PAGE_SIZE = 100;

    private final RetailGatewayBackorderService service;

    @Operation(summary = "미송 대기 목록 (소매 백엔드 → 도매)", description = """
            그 소매처가 아직 못 받은 것만. `OPEN` 인 것만 나오고 **오래된 순**이다 —
            미송은 FIFO 로 풀린다.

            주문번호(`orderNo`)는 안 온다. 그건 소매 통합 주문서의 번호라 도매에 없다.
            대신 `retailOrderId` 를 주니 소매가 자기 DB 에서 채운다.

            도매가 자기 화면에서 직접 넣은 주문의 미송은 안 나온다 — 소매를 안 거쳐서
            소매 DB 에 주문서가 없다.

            에러: 400 `VALIDATION_FAILED` (`size > 100`)""")
    @GetMapping("/backorders")
    public ApiResponse<List<RetailBackorderResponse>> backorders(
            @RequestParam Long retailerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePaging(page, size);

        Paged<RetailBackorderResponse> result = service.search(retailerId, page, size);

        int totalPages = (int) Math.ceil((double) result.totalElements() / size);
        return ApiResponse.paged(result.content(),
                new ApiResponse.PageMeta(page, size, result.totalElements(), totalPages));
    }

    private static void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "page 는 0 이상, size 는 1 이상 %d 이하여야 합니다.".formatted(MAX_PAGE_SIZE));
        }
    }
}
