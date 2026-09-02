package com.ondo.retail.backorder;

import com.ondo.retail.backorder.dto.BackorderResponse;
import com.ondo.retail.common.error.BusinessException;
import com.ondo.retail.common.error.ErrorCode;
import com.ondo.retail.common.response.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미송 대기 현황. <b>지금은 껍데기다 — 목 데이터를 돌려준다.</b>
 *
 * <p>미송 데이터는 도매 DB 에 있다. 소매는 도매 내부 API 로 가져와야 하는데 그게 아직
 * 없어서 목으로 모양만 낸다. 프론트가 화면을 잡을 수 있게 하려는 것이다.
 */
@RestController
@RequestMapping("/api/retail")
@RequiredArgsConstructor
public class BackorderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MockBackorderData mock;

    /**
     * 미송 대기 목록. {@code status = 'OPEN'} 인 것만, <b>오래된 순</b>이다.
     *
     * <p>오래된 순인 건 미송이 FIFO 로 풀리기 때문이다. 위에 있는 줄이 먼저 받는다.
     */
    @GetMapping("/backorders")
    public PageResponse<BackorderResponse> backorders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<BackorderResponse> backorders = mock.backorders();
        return PageResponse.of(
                new PageImpl<>(backorders, PageRequest.of(page, size), backorders.size()));
    }
}
