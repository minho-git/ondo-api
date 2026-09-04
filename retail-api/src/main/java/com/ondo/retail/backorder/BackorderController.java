package com.ondo.retail.backorder;

import com.ondo.retail.backorder.dto.BackorderResponse;
import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미송 대기 현황.
 *
 * <p>미송은 도매 DB 에 있다. 소매는 도매 내부 API 로 가져와 자기 주문번호만 채워 넣는다
 * (MUL-97). 만드는 것도 푸는 것도 도매 몫이고 소매는 읽기만 한다.
 *
 * <p>소매처 id 는 <b>세션에서만</b> 꺼낸다. 요청에 담긴 값을 믿으면 남의 미송을 볼 수 있다.
 */
@Tag(name = "미송", description = "주문했는데 아직 못 받은 것. 도매가 풀고 소매는 읽기만 한다.")
@RestController
@RequestMapping("/api/retail")
@RequiredArgsConstructor
public class BackorderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final BackorderService backorderService;

    /**
     * 미송 대기 목록. {@code status = 'OPEN'} 인 것만, <b>오래된 순</b>이다.
     *
     * <p>오래된 순인 건 미송이 FIFO 로 풀리기 때문이다. 위에 있는 줄이 먼저 받는다.
     */
    @Operation(summary = "미송 대기 현황",
               description = "OPEN 인 것만 오래된 순으로. FIFO 로 풀린다.")
    @GetMapping("/backorders")
    public PageResponse<BackorderResponse> backorders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        return PageResponse.of(
                backorderService.waiting(retailerId(authentication), PageRequest.of(page, size)));
    }

    private static Long retailerId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
