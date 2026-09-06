package com.ondo.retail.wholesale.wholesaler;

import static com.ondo.retail.wholesale.WholesaleCall.call;

import com.ondo.retail.order.WholesalerClient;
import com.ondo.retail.order.dto.WholesalerWithBank;
import com.ondo.retail.wholesale.wholesaler.dto.WholesaleWholesaler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 도매처 정보를 소매 말로 옮긴다 (MUL-98).
 *
 * <p>주문서를 그리려면 계좌가 있어야 해서, 못 읽으면 화면이 못 뜬다. 그래서 상품·미송과
 * 같이 실패를 예외로 던진다 — 주문 접수({@code WholesaleOrderAdapter})만 예외를 안 던진다.
 */
@Component
@RequiredArgsConstructor
public class WholesaleWholesalerAdapter implements WholesalerClient {

    private final WholesaleWholesalerApi api;

    @Override
    public Map<Long, WholesalerWithBank> findAll(List<Long> wholesalerIds) {
        if (wholesalerIds == null || wholesalerIds.isEmpty()) {
            return Map.of();
        }
        List<WholesaleWholesaler> found = call("도매처 조회", () -> api.wholesalers(wholesalerIds)).data();

        Map<Long, WholesalerWithBank> byId = new LinkedHashMap<>();
        for (WholesaleWholesaler w : found) {
            byId.put(w.id(), new WholesalerWithBank(
                    w.id(), w.name(), w.storeBuilding(), w.storeUnit(),
                    w.bankName(), w.bankAccountNo(), w.bankAccountHolder()));
        }
        return byId;
    }
}
